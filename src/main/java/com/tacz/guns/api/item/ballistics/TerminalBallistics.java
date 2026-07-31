package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.BulletType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 终端弹道计算器。
 * <p>
 * 处理弹头击中目标后的行为：穿透判定、跳弹判定、伤害计算。
 * <p>
 * 对应设计文档：C.2.6 穿透与跳弹系统
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class TerminalBallistics {

    /** 跳弹最大角度阈值（度）——小于此角度命中硬表面时触发跳弹 */
    public static final float RICOCHET_ANGLE_THRESHOLD = 15f;

    private TerminalBallistics() {}

    // ====== 穿透判定 ======

    /**
     * 计算穿透力值。
     * <p>
     * 穿透力 = 基础穿透 × 弹头类型修正 × 速度修正 × 距离修正
     *
     * @param basePierce     基础穿透值（来自 BulletData）
     * @param loadedRound    单发弹药数据
     * @param impactVelocity 命中速度（游戏单位/tick）
     * @param muzzleVelocity 初速（游戏单位/tick）
     * @param distance       飞行距离（游戏单位）
     * @return 穿透力值
     */
    public static float calculatePierceValue(float basePierce,
                                              @Nullable LoadedRound loadedRound,
                                              float impactVelocity,
                                              float muzzleVelocity,
                                              float distance) {
        float pierce = basePierce;

        // 弹头类型修正
        if (loadedRound != null) {
            pierce *= loadedRound.bulletType().getPierceModifier();
        }

        // 速度修正：命中速度与初速的比值
        if (muzzleVelocity > 0) {
            float velocityRatio = impactVelocity / muzzleVelocity;
            pierce *= velocityRatio;
        }

        // 距离修正：远距离穿透力衰减
        // 简化模型：每100格衰减10%
        if (distance > 0) {
            float distanceFactor = 1.0f - (distance / 1000f);
            pierce *= Math.max(0.1f, distanceFactor);
        }

        return Math.max(0f, pierce);
    }

    /**
     * 判定是否穿透目标。
     *
     * @param pierceValue     穿透力值
     * @param targetThreshold 目标穿透阈值
     * @return 是否穿透
     */
    public static boolean isPenetration(float pierceValue, float targetThreshold) {
        return pierceValue > targetThreshold;
    }

    /**
     * 计算穿透后的伤害衰减比例。
     * <p>
     * 穿透后伤害按穿透力与阈值的比例衰减。
     *
     * @param pierceValue     穿透力值
     * @param targetThreshold 目标穿透阈值
     * @return 伤害比例（0.3~1.0）
     */
    public static float getPenetrationDamageRatio(float pierceValue, float targetThreshold) {
        if (targetThreshold <= 0) return 1.0f;
        float ratio = pierceValue / targetThreshold;
        // 穿透力越强，伤害衰减越少
        return Math.min(1.0f, Math.max(0.3f, ratio));
    }

    // ====== 跳弹判定 ======

    /**
     * 判定是否触发跳弹。
     * <p>
     * 条件：命中角度 < 15° AND 目标硬度 > 弹头硬度
     *
     * @param hitAngle        命中角度（度），0° = 完全平行
     * @param targetHardness  目标硬度（0.0~1.0）
     * @param bulletHardness  弹头硬度（0.0~1.0）
     * @return 是否跳弹
     */
    public static boolean isRicochet(float hitAngle, float targetHardness, float bulletHardness) {
        return hitAngle < RICOCHET_ANGLE_THRESHOLD && targetHardness > bulletHardness;
    }

    /**
     * 计算跳弹反射方向。
     * <p>
     * 反射方向 = 入射方向关于法线的反射
     *
     * @param incomingDirection 入射方向（归一化）
     * @param surfaceNormal     表面法线（归一化）
     * @return 反射方向（归一化）
     */
    public static Vec3 calculateRicochetDirection(Vec3 incomingDirection, Vec3 surfaceNormal) {
        // 反射公式：r = d - 2(d·n)n
        double dot = incomingDirection.dot(surfaceNormal);
        return incomingDirection.subtract(surfaceNormal.scale(2 * dot)).normalize();
    }

    /**
     * 获取跳弹后的伤害比例。
     * <p>
     * 跳弹弹头伤害衰减为原来的30%。
     *
     * @return 伤害比例（0.3）
     */
    public static float getRicochetDamageRatio() {
        return 0.3f;
    }

    /**
     * 获取跳弹后的速度比例。
     * <p>
     * 跳弹后速度保留原速度的50%。
     *
     * @return 速度比例（0.5）
     */
    public static float getRicochetVelocityRatio() {
        return 0.5f;
    }

    // ====== 伤害计算 ======

    /**
     * 计算终端伤害。
     * <p>
     * 终端伤害 = 基础伤害 × 弹头类型修正 × 稳定性修正 × 速度修正 × 散布修正
     * <p>
     * 注意：此方法不替代TACZ原有的距离衰减伤害系统，
     * 而是作为P2弹药力学扩展的修正层叠加上去。
     *
     * @param baseDamage      基础伤害（来自TACZ距离衰减系统）
     * @param loadedRound     单发弹药数据
     * @param stabilityFactor 稳定性因子（来自 {@link StabilityCalculator}）
     * @param impactVelocity  命中速度
     * @param muzzleVelocity  初速
     * @return 终端伤害
     */
    public static float calculateTerminalDamage(float baseDamage,
                                                  @Nullable LoadedRound loadedRound,
                                                  float stabilityFactor,
                                                  float impactVelocity,
                                                  float muzzleVelocity) {
        float damage = baseDamage;

        // 弹头类型修正
        if (loadedRound != null) {
            damage *= loadedRound.bulletType().getDamageModifier();
        }

        // 稳定性修正（翻滚弹头增加杀伤）
        damage *= StabilityCalculator.getDamageModifier(stabilityFactor);

        // 速度修正：命中速度与初速的比值
        if (muzzleVelocity > 0) {
            float velocityRatio = impactVelocity / muzzleVelocity;
            damage *= Math.max(0.5f, velocityRatio);
        }

        return Math.max(0f, damage);
    }

    // ====== 硬度表 ======

    /**
     * 弹头硬度（取决于弹头类型）。
     * <p>
     * AP弹头最硬（钢/钨核心），HP弹头最软（空尖结构）。
     *
     * @param bulletType 弹头类型
     * @return 硬度值（0.0~1.0）
     */
    public static float getBulletHardness(BulletType bulletType) {
        return switch (bulletType) {
            case FMJ -> 0.5f;
            case HP -> 0.3f;
            case AP -> 0.8f;
            case TRACER -> 0.4f;
            case SUBSONIC -> 0.5f;
            case INCENDIARY -> 0.35f;
        };
    }

    /**
     * 目标材质穿透阈值。
     * <p>
     * 对应设计文档：C.2.6 穿透力表
     *
     * @param materialType 目标材质类型
     * @return 穿透阈值
     */
    public static float getTargetThreshold(TargetMaterial materialType) {
        return materialType.threshold;
    }

    /**
     * 目标材质枚举。
     * <p>
     * 对应设计文档：C.2.6 穿透力表
     */
    public enum TargetMaterial {
        /** 生物（无甲）——几乎所有弹药都能穿透 */
        FLESH(0.1f),
        /** 皮革/木门——几乎所有弹药都能穿透 */
        LEATHER_WOOD(0.2f),
        /** 铁门/石砖——需要步枪弹或穿甲弹 */
        IRON_STONE(0.6f),
        /** 钢板/铁傀儡——需要穿甲弹 */
        STEEL(0.8f),
        /** 生物（铁甲）——需要步枪弹 */
        IRON_ARMOR(0.5f),
        /** 生物（钻石甲）——需要穿甲弹 */
        DIAMOND_ARMOR(0.7f),
        /** 黑曜石/末影——需要特殊弹药 */
        OBSIDIAN(1.0f);

        private final float threshold;

        TargetMaterial(float threshold) {
            this.threshold = threshold;
        }

        public float getThreshold() {
            return threshold;
        }
    }
}
