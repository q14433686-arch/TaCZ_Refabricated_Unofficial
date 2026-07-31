package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.BulletType;
import com.tacz.guns.api.item.enums.PowderType;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import org.jetbrains.annotations.Nullable;

/**
 * 初速计算器。
 * <p>
 * 内弹道→外弹道的关键接口：计算弹头离开枪口时的速度。
 * <p>
 * 对应设计文档：C.2.1 初速模型
 * <p>
 * 核心公式：
 * <pre>
 * V₀ = V_base × √(L_barrel / L_optimal) × √(powder_charge × energy_density) × bullet_mass_modifier × primer_modifier
 * </pre>
 * <p>
 * 其中：
 * <ul>
 *   <li>V_base — 基础初速（来自 BulletData.getSpeed()，由枪包JSON定义）</li>
 *   <li>L_barrel — 实际枪管长度（mm）</li>
 *   <li>L_optimal — 最佳枪管长度（mm，来自 BulletData.getOptimalBarrelLength()）</li>
 *   <li>powder_charge — 装药量（1.0 = 标准）</li>
 *   <li>energy_density — 发射药能量密度</li>
 *   <li>bullet_mass_modifier — 弹头质量修正</li>
 *   <li>primer_modifier — 底火修正</li>
 * </ul>
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class MuzzleVelocityCalculator {

    /** 亚音速弹的初速上限（m/s），约340 m/s（1马赫） */
    public static final float SONIC_SPEED = 340f;

    private MuzzleVelocityCalculator() {}

    /**
     * 计算初速（游戏单位/tick）。
     * <p>
     * 这是弹道系统的核心输出，直接传入 {@code EntityKineticBullet} 的发射速度。
     *
     * @param bulletData    枪包定义的弹道数据（提供 V_base 和 optimalBarrelLength）
     * @param cartridgeType 口径规格（提供 standardBulletMass）
     * @param loadedRound   单发弹药数据（提供装药量、发射药类型、弹头类型、底火类型）
     * @param barrelLength  实际枪管长度（mm），0 表示使用默认值
     * @return 初速（游戏单位/tick）
     */
    public static float calculateMuzzleVelocity(@Nullable BulletData bulletData,
                                                 @Nullable CartridgeType cartridgeType,
                                                 @Nullable LoadedRound loadedRound,
                                                 int barrelLength) {
        if (bulletData == null || loadedRound == null) {
            return bulletData != null ? bulletData.getSpeed() : 5f;
        }

        // 基础初速（来自枪包JSON定义）
        float baseVelocity = bulletData.getSpeed();

        // 枪管长度修正
        float barrelFactor = calculateBarrelLengthFactor(bulletData, barrelLength);

        // 装药量×发射药能量密度修正
        float effectiveCharge = loadedRound.getEffectivePowderCharge();
        float powderEnergy = loadedRound.powderType().getEnergyDensity();
        // 初速与装药量×能量密度的平方根成正比
        float chargeFactor = (float) Math.sqrt(Math.max(0.1f, effectiveCharge * powderEnergy));

        // 弹头质量修正：初速与弹头质量的平方根成反比
        // 标准弹头质量为1.0，更重的弹头初速更低
        float massFactor = (float) (1.0 / Math.sqrt(Math.max(0.1f, loadedRound.bulletType().getMassModifier())));

        // 底火修正：Magnum底火略微提升初速
        float primerFactor = loadedRound.primerType().getIgnitionEnergyModifier();

        // 综合初速
        float velocity = baseVelocity * barrelFactor * chargeFactor * massFactor * primerFactor;

        // 亚音速弹初速上限
        if (loadedRound.bulletType() == BulletType.SUBSONIC) {
            velocity = Math.min(velocity, SONIC_SPEED / 50f); // 转换为游戏单位
        }

        return Math.max(0.5f, velocity);
    }

    /**
     * 计算初速（m/s，用于显示和物理计算）。
     * <p>
     * 游戏速度单位与m/s的换算关系：1 游戏单位/tick ≈ 50 m/s（近似值）。
     * 此换算仅用于显示和物理计算，不用于实际弹道。
     *
     * @return 初速（m/s）
     */
    public static float calculateMuzzleVelocityMs(@Nullable BulletData bulletData,
                                                   @Nullable CartridgeType cartridgeType,
                                                   @Nullable LoadedRound loadedRound,
                                                   int barrelLength) {
        return calculateMuzzleVelocity(bulletData, cartridgeType, loadedRound, barrelLength) * 50f;
    }

    /**
     * 计算枪管长度修正因子。
     * <p>
     * 核心逻辑：
     * <ul>
     *   <li>当 L_barrel < L_optimal 时，初速按比例降低（√(L_barrel/L_optimal)）</li>
     *   <li>当 L_barrel >= L_optimal 时，初速增益递减（上限×1.1）</li>
     * </ul>
     */
    private static float calculateBarrelLengthFactor(BulletData bulletData, int barrelLength) {
        int optimalLength = bulletData.getOptimalBarrelLength();
        if (optimalLength <= 0 || barrelLength <= 0) {
            return 1.0f; // 未定义最佳长度，不修正
        }

        if (barrelLength < optimalLength) {
            // 短枪管：初速按平方根比例降低
            return (float) Math.sqrt((double) barrelLength / optimalLength);
        } else {
            // 长枪管：初速增益递减，上限1.1
            float excess = (float) (barrelLength - optimalLength) / optimalLength;
            float gain = (float) Math.sqrt(1.0 + excess * 0.1);
            return Math.min(gain, 1.1f);
        }
    }

    /**
     * 计算发射药燃速与枪管长度的匹配度。
     * <p>
     * 匹配规则：
     * <ul>
     *   <li>快燃药（黑火药）配短枪管：最佳匹配</li>
     *   <li>快燃药配长枪管：过压风险，初速+5%，炸膛概率+5%</li>
     *   <li>慢燃药（三基药）配长枪管：最佳匹配</li>
     *   <li>慢燃药配短枪管：未充分燃烧，初速-10%，枪口焰+烟雾</li>
     * </ul>
     *
     * @param powderType   发射药类型
     * @param barrelLength 实际枪管长度（mm）
     * @return 匹配度修正因子（0.9~1.05）
     */
    public static float calculateBurnRateMatchFactor(PowderType powderType, int barrelLength) {
        if (barrelLength <= 0) return 1.0f;

        int burnRate = powderType.getBurnRateClass();

        // 简化判定：以 300mm 为分界线
        // 短枪管 < 300mm 适合快燃药（burnRate 4-5）
        // 长枪管 >= 300mm 适合慢燃药（burnRate 1-2）
        boolean isShortBarrel = barrelLength < 300;

        if (isShortBarrel && burnRate <= 2) {
            // 慢燃药配短枪管：未充分燃烧
            return 0.9f;
        } else if (!isShortBarrel && burnRate >= 4) {
            // 快燃药配长枪管：过压风险
            return 1.05f;
        }
        return 1.0f;
    }

    /**
     * 发射药燃速与枪管不匹配时的附加炸膛概率。
     *
     * @param powderType   发射药类型
     * @param barrelLength 实际枪管长度（mm）
     * @return 附加炸膛概率（0.0~0.05）
     */
    public static float getBurnRateMismatchCatastrophicProbability(PowderType powderType, int barrelLength) {
        if (barrelLength <= 0) return 0f;

        int burnRate = powderType.getBurnRateClass();
        boolean isLongBarrel = barrelLength >= 300;

        // 快燃药配长枪管：过压风险
        if (isLongBarrel && burnRate >= 4) {
            return 0.05f; // +5%炸膛概率
        }
        return 0f;
    }
}
