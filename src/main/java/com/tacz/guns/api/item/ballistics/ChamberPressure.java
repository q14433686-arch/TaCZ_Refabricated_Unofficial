package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.PowderType;
import org.jetbrains.annotations.Nullable;

/**
 * 膛压计算模型。
 * <p>
 * 内弹道核心：计算发射药在膛内燃烧产生的峰值膛压。
 * 膛压是炸膛判定、初速计算、枪管磨损的基础输入。
 * <p>
 * 简化物理模型：
 * <pre>
 * 峰值膛压 = 标准膛压 × 装药量修正 × 发射药能量密度修正 × 底火修正 × 枪管长度修正
 * </pre>
 * <p>
 * 对应设计文档：C.2.1 初速模型 & B.2.6 定量装药的后果
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class ChamberPressure {

    private ChamberPressure() {}

    /**
     * 计算峰值膛压（MPa）。
     * <p>
     * 核心公式：
     * <pre>
     * P_peak = P_max_safe × charge_ratio × powder_energy × primer_energy × barrel_length_factor
     * </pre>
     * <p>
     * 其中：
     * <ul>
     *   <li>P_max_safe — 口径的最大安全膛压（来自 {@link CartridgeType#maxSafePressure()}）</li>
     *   <li>charge_ratio — 实际装药量 / 标准装药量（来自 {@link LoadedRound#getEffectivePowderCharge()}）</li>
     *   <li>powder_energy — 发射药能量密度修正（来自 {@link PowderType#getEnergyDensity()}）</li>
     *   <li>primer_energy — 底火点火能量修正（来自 {@link LoadedRound#primerType()}）</li>
     *   <li>barrel_length_factor — 枪管长度修正（短枪管→膛压峰值更高；长枪管→更充分膨胀→峰值更低）</li>
     * </ul>
     *
     * @param cartridgeType 口径规格（提供 maxSafePressure 和物理常量）
     * @param loadedRound   单发弹药数据（提供装药量、发射药类型、底火类型）
     * @param barrelLength  实际枪管长度（mm），0 表示使用默认值
     * @return 峰值膛压（MPa）
     */
    public static float calculatePeakPressure(@Nullable CartridgeType cartridgeType,
                                              LoadedRound loadedRound,
                                              int barrelLength) {
        if (cartridgeType == null || loadedRound == null) {
            return 0f;
        }

        // 基准：口径的最大安全膛压
        float basePressure = cartridgeType.maxSafePressure();

        // 装药量修正：实际装药量与标准装药量的比值
        // 装药量与膛压的关系近似为二次方（简化模型）
        float effectiveCharge = loadedRound.getEffectivePowderCharge();
        float chargeRatio = effectiveCharge * effectiveCharge;

        // 发射药能量密度修正
        float powderEnergy = loadedRound.powderType().getEnergyDensity();

        // 底火点火能量修正
        float primerEnergy = loadedRound.primerType().getIgnitionEnergyModifier();

        // 枪管长度修正
        float barrelFactor = calculateBarrelLengthFactor(cartridgeType, barrelLength);

        return basePressure * chargeRatio * powderEnergy * primerEnergy * barrelFactor;
    }

    /**
     * 判断膛压是否超过安全阈值。
     * <p>
     * 当峰值膛压超过口径最大安全膛压时，有炸膛风险。
     *
     * @param peakPressure  峰值膛压
     * @param cartridgeType 口径规格
     * @return 是否超压
     */
    public static boolean isOverPressure(float peakPressure, @Nullable CartridgeType cartridgeType) {
        if (cartridgeType == null) return false;
        return peakPressure > cartridgeType.maxSafePressure();
    }

    /**
     * 计算超压程度（0.0 = 安全，1.0 = 超压100%，2.0 = 超压200%）。
     * <p>
     * 用于P3过热/炸膛系统的概率模型输入。
     *
     * @param peakPressure  峰值膛压
     * @param cartridgeType 口径规格
     * @return 超压比例（0.0+）
     */
    public static float getOverPressureRatio(float peakPressure, @Nullable CartridgeType cartridgeType) {
        if (cartridgeType == null) return 0f;
        return Math.max(0f, peakPressure / cartridgeType.maxSafePressure() - 1.0f);
    }

    /**
     * 计算炸膛概率（每发）。
     * <p>
     * 概率模型：
     * <pre>
     * P_catastrophic = base_rate × overpressure_ratio × barrel_wear_factor
     * </pre>
     * <p>
     * 其中：
     * <ul>
     *   <li>base_rate — 基础炸膛概率（0.001 = 0.1%）</li>
     *   <li>overpressure_ratio — 超压比例（来自 {@link #getOverPressureRatio}）</li>
     *   <li>barrel_wear_factor — 枪管磨损因子（来自 GunWearData，1.0 = 无磨损）</li>
     * </ul>
     *
     * @param peakPressure     峰值膛压
     * @param cartridgeType    口径规格
     * @param barrelWearFactor 枪管磨损因子（1.0 = 无磨损，2.0 = 严重磨损）
     * @return 炸膛概率（0.0~1.0）
     */
    public static float calculateCatastrophicFailureProbability(float peakPressure,
                                                                 @Nullable CartridgeType cartridgeType,
                                                                 float barrelWearFactor) {
        if (cartridgeType == null) return 0f;
        float overPressureRatio = getOverPressureRatio(peakPressure, cartridgeType);
        if (overPressureRatio <= 0f) return 0f;

        // 基础炸膛概率：0.1%
        float baseRate = 0.001f;

        // 超压比例与炸膛概率的关系：二次方增长
        float overPressureFactor = overPressureRatio * overPressureRatio;

        // 枪管磨损放大
        float wearFactor = Math.max(1.0f, barrelWearFactor);

        return Math.min(1.0f, baseRate * overPressureFactor * wearFactor * 100f);
    }

    /**
     * 计算枪管长度修正因子。
     * <p>
     * 短枪管：膛压峰值更高（燃气膨胀不充分，峰值更高但持续时间短）
     * 长枪管：膛压峰值更低但更充分膨胀（初速更高但峰值更低）
     * <p>
     * 简化模型：以口径的整体长度为参考基准
     *
     * @param cartridgeType 口径规格
     * @param barrelLength  实际枪管长度（mm）
     * @return 修正因子（>1.0 = 峰值更高，<1.0 = 峰值更低）
     */
    private static float calculateBarrelLengthFactor(CartridgeType cartridgeType, int barrelLength) {
        if (barrelLength <= 0) {
            return 1.0f; // 默认值，不修正
        }

        // 参考枪管长度：口径整体长度 × 15（经验值）
        float referenceLength = cartridgeType.overallLength() * 15f;

        if (barrelLength < referenceLength * 0.5f) {
            // 极短枪管：膛压峰值偏高约 15%
            return 1.15f;
        } else if (barrelLength < referenceLength * 0.8f) {
            // 短枪管：膛压峰值偏高约 5%
            return 1.05f;
        } else if (barrelLength > referenceLength * 1.5f) {
            // 长枪管：膛压峰值偏低约 5%
            return 0.95f;
        }
        return 1.0f;
    }
}
