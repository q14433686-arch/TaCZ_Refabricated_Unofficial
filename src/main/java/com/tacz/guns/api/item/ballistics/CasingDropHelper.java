package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.cartridge.CartridgeTypeManager;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.CaseCondition;
import com.tacz.guns.api.item.enums.CaseMaterial;
import com.tacz.guns.entity.EntityCasingDrop;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 弹壳掉落辅助工具。
 * <p>
 * 在射击后自动在抛壳位置生成弹壳实体。
 * <p>
 * 对应设计文档：B.2.5 复装系统完整流程 - 步骤1：回收空弹壳
 * <p>
 * 使用方式：
 * <pre>
 * // 在射击逻辑中调用
 * CasingDropHelper.spawnCasing(serverLevel, shooter, loadedRound);
 * </pre>
 */
public final class CasingDropHelper {

    /**
     * 在射击者位置生成弹壳实体（不指定 ammoId，使用 cartridgeType 作为备选）。
     */
    public static void spawnCasing(ServerLevel level, LivingEntity shooter,
                                    @Nullable LoadedRound loadedRound) {
        spawnCasing(level, shooter, loadedRound, null);
    }

    /** 默认抛壳速度 */
    private static final float DEFAULT_EJECT_SPEED = 0.3f;

    /** 默认抛壳方向（1=右侧，-1=左侧） */
    private static final int DEFAULT_EJECT_SIDE = 1;

    private CasingDropHelper() {}

    /**
     * 在射击者位置生成弹壳实体。
     * <p>
     * 弹壳从射击者的抛壳口位置（肩膀高度偏右上方）弹出，
     * 带有随机旋转和速度偏差。
     *
     * @param level       服务器世界
     * @param shooter     射击者
     * @param loadedRound 已击发的弹药数据
     * @param ammoId      弹药ID（对应枪包的 ammo index，用于查找 shell 模型）
     */
    public static void spawnCasing(ServerLevel level, LivingEntity shooter,
                                    @Nullable LoadedRound loadedRound,
                                    @Nullable Identifier ammoId) {
        if (loadedRound == null) return;

        // 计算弹壳击发后的状态
        CaseCondition postFireCondition = calculatePostFireCondition(loadedRound);

        // 弹壳材质
        CaseMaterial caseMaterial = loadedRound.caseMaterial();

        // 口径类型
        Identifier cartridgeType = loadedRound.cartridgeType();

        // 生成位置：射手肩膀高度偏右
        Vec3 shooterPos = shooter.position();
        double x = shooterPos.x;
        double y = shooterPos.y + shooter.getEyeHeight() * 0.8;
        double z = shooterPos.z;

        // 创建弹壳实体
        EntityCasingDrop casing = new EntityCasingDrop(
                level, x, y, z,
                cartridgeType,
                ammoId,
                caseMaterial,
                postFireCondition.name().toLowerCase()
        );

        // 设置抛壳方向
        float yaw = shooter.getYRot();
        casing.setEjectDirection(yaw, DEFAULT_EJECT_SIDE, DEFAULT_EJECT_SPEED);

        // 添加到世界
        level.addFreshEntity(casing);
    }

    /**
     * 计算击发后的弹壳状态。
     * <p>
     * 击发后弹壳状态会变化：
     * <ul>
     *   <li>PRISTINE → GOOD（首次击发后变为良好）</li>
     *   <li>GOOD → GOOD（保持良好，但有概率变为WORN）</li>
     *   <li>WORN → WORN（保持磨损，但有概率变为CRACKED）</li>
     * </ul>
     * <p>
     * 装药过量会增加弹壳状态恶化的概率。
     *
     * @param loadedRound 击发前的弹药数据
     * @return 击发后的弹壳状态
     */
    private static CaseCondition calculatePostFireCondition(LoadedRound loadedRound) {
        CaseCondition current = loadedRound.caseCondition();

        // 装药过量增加弹壳恶化概率
        boolean isOvercharged = loadedRound.isOvercharged();

        return switch (current) {
            case PRISTINE -> CaseCondition.GOOD; // 首次击发必定变为良好
            case GOOD -> {
                // 10%概率变为磨损（装药过量时30%）
                yield isOvercharged ? CaseCondition.WORN : CaseCondition.GOOD;
            }
            case WORN -> {
                // 20%概率变为裂纹（装药过量时50%）
                yield isOvercharged ? CaseCondition.CRACKED : CaseCondition.WORN;
            }
            case CRACKED, DEFORMED, CORRODED -> current; // 已损坏的弹壳保持不变
        };
    }

    /**
     * 判断弹壳是否可拾取（用于复装）。
     * <p>
     * 只有状态为 GOOD 或 WORN 的弹壳才有复装价值。
     * PRISTINE 不会出现（击发后必定变为 GOOD）。
     * CRACKED/DEFORMED/CORRODED 的弹壳不可复装。
     *
     * @param condition 弹壳状态
     * @return 是否可拾取用于复装
     */
    public static boolean isCasePickupableForReloading(CaseCondition condition) {
        return condition == CaseCondition.GOOD || condition == CaseCondition.WORN;
    }
}
