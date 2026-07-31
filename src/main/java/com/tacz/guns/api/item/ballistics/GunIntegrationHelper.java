package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunMaintenanceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.GunWearData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.GunCycleState;
import com.tacz.guns.api.item.enums.MalfunctionType;
import com.tacz.guns.api.item.operation.GunCycleMachine;
import com.tacz.guns.init.ModDataComponents;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械系统集成辅助类。
 * <p>
 * 将 P1 状态机、P2 弹道、P3 过热/保养等新系统
 * 与 TACZ 现有的射击流（preCheck → shoot → reduceAmmoOnce）桥接。
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 * <p>
 * 对应看板：P1集成（状态机接入射击流）+ P3集成（过热→精度修正）
 */
public final class GunIntegrationHelper {

    private GunIntegrationHelper() {}

    // ====== 射击前检查（在 preCheck 中调用） ======

    /**
     * 检查枪械是否处于可射击状态。
     * <p>
     * 综合检查 GunStateData 的以下状态：
     * <ul>
     *   <li>故障状态（MALFUNCTION）→ 不可射击</li>
     *   <li>Squib（枪管内有弹头）→ 不可射击（极高风险）</li>
     *   <li>保险开启 → 不可射击</li>
     *   <li>枪膛内无弹且非开膛待击 → 不可射击</li>
     * </ul>
     *
     * @param gunItem 枪械物品
     * @return 如果可以射击返回 null，否则返回阻止原因字符串
     */
    @Nullable
    public static String checkCanFire(ItemStack gunItem) {
        // 确保 GunStateData 存在
        ensureGunStateDataExists(gunItem);

        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData == null) {
            return null; // 无状态数据，使用旧逻辑
        }

        // 保险检查
        if (stateData.safetyOn()) {
            return "SAFETY_ON";
        }

        // 故障检查
        if (stateData.hasMalfunction()) {
            return "MALFUNCTION:" + stateData.malfunctionType().name();
        }

        // Squib检查（枪管内有弹头，极高风险）
        if (stateData.hasSquibInBarrel()) {
            return "SQUIB_IN_BARREL";
        }

        return null;
    }

    // ====== 射击后状态机循环（在 shootOnce 中调用） ======

    /**
     * 射击后执行枪机循环。
     * <p>
     * 根据枪机类型（开膛/闭膛/手动）执行相应的循环：
     * <ul>
     *   <li>开膛待击/闭膛待击（自动）：执行 fireAutoCycle</li>
     *   <li>手动：不执行自动循环（等玩家手动拉栓）</li>
     * </ul>
     * <p>
     * 循环结果会更新到 ItemStack 的 DataComponent 中：
     * <ul>
     *   <li>GunStateData（枪机循环状态、故障、烧蚀等）</li>
     *   <li>GunWearData（磨损消耗）</li>
     *   <li>FeedDeviceData（供弹具消耗）</li>
     * </ul>
     *
     * @param gunItem  枪械物品
     * @param gunData  枪械数据
     * @param shooter  射击者
     * @return 循环结果
     */
    public static GunCycleMachine.CycleResult postShootCycle(ItemStack gunItem, GunData gunData,
                                                              @Nullable LivingEntity shooter) {
        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData == null) {
            return GunCycleMachine.CycleResult.fail("No GunStateData");
        }

        // 手动枪机不执行自动循环
        if (gunData.getBolt() == Bolt.MANUAL_ACTION) {
            // 手动枪机：击发后枪膛为空，状态变为 EMPTY
            GunStateData updatedState = stateData.withChamberedRoundFired();
            gunItem.set(ModDataComponents.GUN_STATE_DATA, updatedState);

            // 仍然消耗扳机组磨损
            GunWearData wearData = gunItem.get(ModDataComponents.GUN_WEAR_DATA);
            if (wearData != null) {
                boolean overcharged = stateData.chamberedRound() != null && stateData.chamberedRound().isOvercharged();
                boolean corrosive = stateData.chamberedRound() != null && stateData.chamberedRound().isCorrosive();
                wearData = wearData.withShootWear(overcharged, corrosive);
                gunItem.set(ModDataComponents.GUN_WEAR_DATA, wearData);
            }

            return GunCycleMachine.CycleResult.ok(updatedState, null, wearData, null);
        }

        // 自动/闭膛枪机：执行完整循环
        GunWearData wearData = gunItem.get(ModDataComponents.GUN_WEAR_DATA);
        if (wearData == null) {
            wearData = GunWearData.createDefault();
        }

        FeedDeviceData feedDevice = gunItem.get(ModDataComponents.FEED_DEVICE_DATA);

        GunCycleMachine.CycleResult result = GunCycleMachine.fireAutoCycle(stateData, feedDevice, wearData);

        // 应用结果到 ItemStack
        if (result.success() && result.state() != null) {
            gunItem.set(ModDataComponents.GUN_STATE_DATA, result.state());
        } else if (!result.success() && result.state() != null) {
            // 故障状态也需要写入
            gunItem.set(ModDataComponents.GUN_STATE_DATA, result.state());
        }

        if (result.updatedWear() != null) {
            gunItem.set(ModDataComponents.GUN_WEAR_DATA, result.updatedWear());
        }

        if (result.updatedDevice() != null) {
            gunItem.set(ModDataComponents.FEED_DEVICE_DATA, result.updatedDevice());
        }

        // 同步 TACZ 旧系统的 hasBulletInBarrel / ammoCount
        syncLegacyAmmoState(gunItem, gunData);

        return result;
    }

    // ====== 拉栓后状态机循环（在 tickBolt 中调用） ======

    /**
     * 手动拉栓循环。
     * <p>
     * 在玩家拉栓后调用，更新枪膛状态和供弹具数据。
     *
     * @param gunItem  枪械物品
     * @param gunData  枪械数据
     * @return 循环结果
     */
    public static GunCycleMachine.CycleResult postBoltCycle(ItemStack gunItem, GunData gunData) {
        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData == null) {
            return GunCycleMachine.CycleResult.fail("No GunStateData");
        }

        GunWearData wearData = gunItem.get(ModDataComponents.GUN_WEAR_DATA);
        if (wearData == null) {
            wearData = GunWearData.createDefault();
        }

        FeedDeviceData feedDevice = gunItem.get(ModDataComponents.FEED_DEVICE_DATA);

        GunCycleMachine.CycleResult result = GunCycleMachine.manualBoltCycle(stateData, feedDevice, wearData);

        // 应用结果
        if (result.state() != null) {
            gunItem.set(ModDataComponents.GUN_STATE_DATA, result.state());
        }
        if (result.updatedWear() != null) {
            gunItem.set(ModDataComponents.GUN_WEAR_DATA, result.updatedWear());
        }
        if (result.updatedDevice() != null) {
            gunItem.set(ModDataComponents.FEED_DEVICE_DATA, result.updatedDevice());
        }

        // 同步 TACZ 旧系统
        syncLegacyAmmoState(gunItem, gunData);

        return result;
    }

    // ====== 综合精度修正（在 lerpInaccuracy 中调用） ======

    /**
     * 计算综合精度修正系数。
     * <p>
     * 综合以下系统的精度影响：
     * <ol>
     *   <li>基础过热精度（TACZ 原始 lerpInaccuracy）</li>
     *   <li>过热扩展精度（OverheatExpansion.getHeatInaccuracyModifier）</li>
     *   <li>模块化耐久精度（GunWearData.calculateOverallAccuracy）</li>
     *   <li>保养状态精度（GunMaintenanceData.getAccuracyPenalty）</li>
     *   <li>烧蚀精度（GunStateData.getErosionAccuracyPenalty）</li>
     *   <li>环境卡壳修正（EnvironmentSensor）</li>
     * </ol>
     * <p>
     * 所有修正系数以乘法叠加：
     * <pre>
     * final_inaccuracy = base_inaccuracy × heat_inaccuracy × overheat_expansion ×
     *                    (1/wear_accuracy) × (1/maintenance_accuracy) × (1/erosion_accuracy)
     * </pre>
     * <p>
     * 注意：accuracy 修正系数（<1.0 = 精度下降）需取倒数变为 inaccuracy 系数（>1.0 = 散布增大）。
     *
     * @param gunItem        枪械物品
     * @param baseInaccuracy TACZ 原始 lerpInaccuracy 值
     * @return 综合修正后的散布系数
     */
    public static float getComprehensiveInaccuracy(ItemStack gunItem, float baseInaccuracy) {
        float inaccuracy = baseInaccuracy;

        // 1. 过热扩展精度修正
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun != null) {
            GunHeatData heatData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem))
                    .map(index -> index.getGunData().getHeatData())
                    .orElse(null);
            if (heatData != null) {
                float heatPercentage = iGun.getHeatAmount(gunItem) / heatData.getHeatMax();
                inaccuracy *= OverheatExpansion.getHeatInaccuracyModifier(heatPercentage);
            }
        }

        // 2. 模块化耐久精度修正（accuracy → inaccuracy 取倒数）
        GunWearData wearData = gunItem.get(ModDataComponents.GUN_WEAR_DATA);
        if (wearData != null) {
            float accuracy = wearData.calculateOverallAccuracy();
            if (accuracy < 1.0f && accuracy > 0.0f) {
                inaccuracy /= accuracy;
            }
        }

        // 3. 保养状态精度修正
        GunMaintenanceData maintenanceData = gunItem.get(ModDataComponents.GUN_MAINTENANCE_DATA);
        if (maintenanceData != null) {
            float maintenanceAccuracy = maintenanceData.getAccuracyPenalty();
            if (maintenanceAccuracy < 1.0f && maintenanceAccuracy > 0.0f) {
                inaccuracy /= maintenanceAccuracy;
            }
        }

        // 4. 烧蚀精度修正
        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData != null) {
            float erosionPenalty = stateData.getErosionAccuracyPenalty();
            if (erosionPenalty > 0.0f) {
                inaccuracy /= (1.0f - erosionPenalty);
            }
        }

        return inaccuracy;
    }

    // ====== 换弹完成后同步状态 ======

    /**
     * 换弹完成后同步 GunStateData。
     * <p>
     * TACZ 的换弹逻辑会更新 hasBulletInBarrel 和 ammoCount，
     * 此方法将这些变更同步到 GunStateData。
     *
     * @param gunItem  枪械物品
     * @param gunData  枪械数据
     */
    public static void syncAfterReload(ItemStack gunItem, GunData gunData) {
        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData == null) {
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        // 清除故障状态（换弹过程中可以清除部分故障）
        if (stateData.hasMalfunction()) {
            // 仅 FAILURE_TO_FEED 类型的故障可以在换弹时清除
            if (stateData.malfunctionType() == MalfunctionType.FAILURE_TO_FEED) {
                stateData = stateData.withMalfunctionCleared();
            }
        }

        // 同步膛内弹药状态
        boolean hasBulletInBarrel = iGun.hasBulletInBarrel(gunItem);
        if (hasBulletInBarrel && !stateData.hasChamberedRound()) {
            // 旧系统有膛内弹药，新系统没有 → 创建默认的膛内弹药
            LoadedRound defaultRound = LoadedRound.createDefault();
            stateData = stateData.withChamberedRound(defaultRound);
        } else if (!hasBulletInBarrel && stateData.hasChamberedRound()) {
            // 旧系统没有膛内弹药，新系统有 → 清空
            stateData = stateData.withChamberedRoundFired();
        }

        // 如果处于 EMPTY 状态且有弹药，转为 READY
        if (stateData.cycleState() == GunCycleState.EMPTY && stateData.hasChamberedRound()) {
            stateData = stateData.withCycleState(GunCycleState.READY);
        }

        gunItem.set(ModDataComponents.GUN_STATE_DATA, stateData);
    }

    // ====== 内部辅助方法 ======

    /**
     * 同步 TACZ 旧系统的弹药状态。
     * <p>
     * 从 GunStateData 的状态推算 TACZ 旧系统需要的：
     * <ul>
     *   <li>hasBulletInBarrel（是否膛内有弹）</li>
     * </ul>
     * <p>
     * 注意：ammoCount 不在此处同步，因为 TACZ 的弹匣计数逻辑
     * 已在 reduceAmmoOnce / putAmmoInMagazine 中正确维护。
     *
     * @param gunItem 枪械物品
     * @param gunData 枪械数据
     */
    private static void syncLegacyAmmoState(ItemStack gunItem, GunData gunData) {
        GunStateData stateData = gunItem.get(ModDataComponents.GUN_STATE_DATA);
        if (stateData == null) {
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        // 同步膛内弹药状态
        boolean shouldHaveBulletInBarrel = stateData.hasChamberedRound() && gunData.getBolt() != Bolt.OPEN_BOLT;
        boolean currentHasBulletInBarrel = iGun.hasBulletInBarrel(gunItem);

        if (shouldHaveBulletInBarrel != currentHasBulletInBarrel) {
            iGun.setBulletInBarrel(gunItem, shouldHaveBulletInBarrel);
        }
    }

    /**
     * 初始化枪械的 GunStateData（如果不存在）。
     * <p>
     * 在首次使用枪械时调用，确保 GunStateData 存在。
     *
     * @param gunItem 枪械物品
     */
    public static void ensureGunStateDataExists(ItemStack gunItem) {
        if (gunItem.get(ModDataComponents.GUN_STATE_DATA) == null) {
            // 从旧系统状态初始化
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun != null) {
                boolean hasBulletInBarrel = iGun.hasBulletInBarrel(gunItem);
                GunStateData stateData;
                if (hasBulletInBarrel) {
                    stateData = GunStateData.createDefault();
                    // 旧系统有膛内弹药，创建默认膛内弹药
                    stateData = stateData.withChamberedRound(LoadedRound.createDefault());
                } else {
                    int ammoCount = iGun.getCurrentAmmoCount(gunItem);
                    stateData = ammoCount > 0 ? GunStateData.createDefault() : GunStateData.createEmpty();
                }
                gunItem.set(ModDataComponents.GUN_STATE_DATA, stateData);
            }
        }
    }
}
