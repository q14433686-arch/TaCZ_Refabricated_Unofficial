package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.component.GunMaintenanceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.GunWearData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.component.ToleranceData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 炸膛系统。
 * <p>
 * 炸膛(Catastrophic Failure)是枪械最严重的故障类型，发生在膛压超过枪管承受极限时。
 * <p>
 * 对应设计文档：F. 炸膛系统
 * <p>
 * 核心逻辑：
 * <ol>
 *   <li>计算炸膛综合评分（加权判定模型）</li>
 *   <li>将评分转换为概率：probability = min(95%, score × 0.01)</li>
 *   <li>随机判定是否炸膛</li>
 *   <li>确定严重度（1=轻微，2=中等，3=严重）</li>
 *   <li>应用后果</li>
 * </ol>
 * <p>
 * 触发条件汇总：
 * <ul>
 *   <li>超量装药(120%): +3.0分</li>
 *   <li>严重超量装药(150%): +10.0分</li>
 *   <li>Squib未清理继续射击: +80.0分</li>
 *   <li>枪管异物(泥/沙/水): +1.5~3.0分</li>
 *   <li>极端过热(>70%): 最高+15.0分</li>
 *   <li>公差差: 最高+5.0分</li>
 *   <li>枪管损伤: +3.0~10.0分</li>
 *   <li>严重烧蚀: +5.0分</li>
 * </ul>
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class CatastrophicFailureSystem {

    private CatastrophicFailureSystem() {}

    /**
     * 检查并应用炸膛判定。
     * <p>
     * 在每次射击时调用。如果判定炸膛，将应用后果并返回炸膛结果。
     *
     * @param gunStateData    枪械运行状态数据
     * @param maintenanceData 枪械保养数据
     * @param heatPercentage  当前热量百分比（0.0~1.0）
     * @param heatData        过热数据（提供阈值）
     * @param toleranceData   公差数据
     * @param wearData        磨损数据（炸膛后果会修改磨损）
     * @param gunStack        枪械物品堆
     * @param shooter         射击者（用于受伤判定）
     * @return 炸膛结果，null表示未炸膛
     */
    @Nullable
    public static CatastrophicResult checkAndApply(GunStateData gunStateData,
                                                    GunMaintenanceData maintenanceData,
                                                    float heatPercentage,
                                                    GunHeatData heatData,
                                                    @Nullable ToleranceData toleranceData,
                                                    GunWearData wearData,
                                                    ItemStack gunStack,
                                                    @Nullable LivingEntity shooter) {
        // 1. 计算综合评分
        float toleranceScore = toleranceData != null ? toleranceData.score() : 50.0f;
        float score = gunStateData.getCatastrophicFailureScore(
                heatPercentage,
                heatData.getCatastrophicHeatThreshold(),
                toleranceScore
        );

        // 额外因素：积碳严重
        if (maintenanceData.carbonFoulingLevel() > 80) {
            score += 5.0f;
        }

        // 额外因素：锈蚀严重
        if (maintenanceData.corrosionLevel() > 75) {
            score += 5.0f;
        }

        // 2. 如果评分为0，直接返回（无风险）
        if (score <= 0.0f) {
            return null;
        }

        // 3. 转换为概率
        float probability = Math.min(0.95f, score * 0.01f);

        // 4. 随机判定
        if (gunStack.isEmpty() || Math.random() >= probability) {
            return null; // 未炸膛
        }

        // 5. 确定严重度
        int severity = GunStateData.getCatastrophicSeverity(score);

        // 6. 应用后果
        return applyCatastrophicFailure(severity, gunStateData, wearData, gunStack, shooter);
    }

    /**
     * 应用炸膛后果。
     * <p>
     * 严重度分级：
     * <ul>
     *   <li>1（轻微）：枪管损伤+1，精度永久下降20%</li>
     *   <li>2（中等）：枪管/枪机部件损毁(barrelDamageLevel=3)，大幅磨损</li>
     *   <li>3（严重）：武器报废（无法修复），射手受伤3-8点</li>
     * </ul>
     *
     * @param severity     严重度（1-3）
     * @param gunStateData 枪械运行状态数据
     * @param wearData     磨损数据
     * @param gunStack     枪械物品堆
     * @param shooter      射击者
     * @return 炸膛结果
     */
    private static CatastrophicResult applyCatastrophicFailure(int severity,
                                                                GunStateData gunStateData,
                                                                GunWearData wearData,
                                                                ItemStack gunStack,
                                                                @Nullable LivingEntity shooter) {
        // 应用枪管损伤
        GunStateData updatedState = gunStateData.withBarrelDamageFromCatastrophic(severity);

        // 应用磨损
        GunWearData updatedWear = switch (severity) {
            case 1 -> wearData.withShootWear(false, false); // 轻微：一次正常磨损
            case 2 -> wearData.withShootWear(true, false)   // 中等：一次过量磨损
                    .withShootWear(true, false)
                    .withShootWear(true, false);              // 3倍过量磨损
            case 3 -> wearData.forceReplace(                 // 严重：强制替换所有部件（武器报废）
                    GunWearData.ComponentType.BARREL,
                    GunWearData.ComponentType.BOLT,
                    GunWearData.ComponentType.RECEIVER
            );
            default -> wearData;
        };

        // 严重炸膛对射手造成伤害
        float damageToShooter = 0f;
        if (severity >= 2 && shooter != null) {
            damageToShooter = severity == 2 ? 3.0f : 8.0f;
            // 对射手造成伤害
            DamageSource damageSource = shooter.damageSources().generic();
            shooter.hurt(damageSource, damageToShooter);
            // 播放爆炸音效
            if (shooter.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.2f);
            }
        }

        return new CatastrophicResult(severity, updatedState, updatedWear, damageToShooter);
    }

    /**
     * 炸膛结果记录。
     */
    public record CatastrophicResult(
            /** 严重度（1=轻微，2=中等，3=严重） */
            int severity,
            /** 更新后的枪械运行状态数据 */
            GunStateData updatedGunStateData,
            /** 更新后的磨损数据 */
            GunWearData updatedWearData,
            /** 对射手造成的伤害 */
            float damageToShooter
    ) {
        /** 是否为轻微炸膛 */
        public boolean isMinor() { return severity == 1; }
        /** 是否为中等炸膛 */
        public boolean isModerate() { return severity == 2; }
        /** 是否为严重炸膛（武器报废） */
        public boolean isSevere() { return severity == 3; }
    }
}
