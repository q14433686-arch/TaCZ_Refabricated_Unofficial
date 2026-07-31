package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.BulletType;
import org.jetbrains.annotations.Nullable;

/**
 * 弹头稳定性计算器（Greenhill公式）。
 * <p>
 * 膛线赋予弹头旋转，使其在飞行中保持稳定。缠距与弹头参数的匹配
 * 决定弹头是否稳定飞行——不匹配会导致弹头翻滚、精度严重下降。
 * <p>
 * 对应设计文档：C.2.5 弹头稳定性：膛线缠距与弹头匹配
 * <p>
 * 游戏化Greenhill公式（简化版）：
 * <pre>
 * Sg = 150 × D² / (T × L)
 *
 * 其中：
 * - D: 口径（英寸，由弹药JSON定义）
 * - T: 缠距（英寸/转，由枪管JSON定义）
 * - L: 弹头长度（英寸，由弹头类型决定）
 *
 * 判定：
 * - Sg > 1.5: 完全稳定，精度无惩罚
 * - 1.0 < Sg ≤ 1.5: 边缘稳定，精度-10%
 * - 0.5 < Sg ≤ 1.0: 不稳定，弹头翻滚，精度-50%，伤害+20%（翻滚增加杀伤）
 * - Sg ≤ 0.5: 严重失稳，精度-80%，弹头可能过早解体
 * </pre>
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class StabilityCalculator {

    /** 完全稳定阈值 */
    public static final float STABLE_THRESHOLD = 1.5f;
    /** 边缘稳定阈值 */
    public static final float MARGINAL_THRESHOLD = 1.0f;
    /** 不稳定阈值 */
    public static final float UNSTABLE_THRESHOLD = 0.5f;

    private StabilityCalculator() {}

    /**
     * 计算弹头稳定性因子（Sg）。
     * <p>
     * 使用游戏化Greenhill公式。
     *
     * @param cartridgeType 口径规格（提供弹头直径）
     * @param loadedRound   单发弹药数据（提供弹头类型）
     * @param twistRate     枪管缠距（英寸/转），如7表示1:7缠距，0表示使用默认值
     * @param bulletLength  弹头长度（英寸），0表示从口径推算
     * @return 稳定性因子（Sg）
     */
    public static float calculateStabilityFactor(@Nullable CartridgeType cartridgeType,
                                                  @Nullable LoadedRound loadedRound,
                                                  int twistRate,
                                                  float bulletLength) {
        if (cartridgeType == null || loadedRound == null) {
            return STABLE_THRESHOLD; // 默认完全稳定
        }

        // 弹头直径（mm → 英寸，1mm ≈ 0.03937英寸）
        float diameterInches = cartridgeType.bulletDiameter() * 0.03937f;

        // 弹头长度（英寸）
        float lengthInches = calculateEffectiveBulletLength(cartridgeType, loadedRound.bulletType(), bulletLength);

        // 缠距（英寸/转）
        // 如果未指定缠距，使用Greenhill推荐缠距
        float twistInches;
        if (twistRate <= 0) {
            twistInches = calculateRecommendedTwistRate(diameterInches, lengthInches);
        } else {
            twistInches = (float) twistRate;
        }

        // Greenhill公式：Sg = 150 × D² / (T × L)
        float sg = 150f * diameterInches * diameterInches / (twistInches * lengthInches);

        return Math.max(0f, sg);
    }

    /**
     * 获取稳定性分类。
     *
     * @param sg 稳定性因子
     * @return 稳定性分类
     */
    public static StabilityCategory getStabilityCategory(float sg) {
        if (sg > STABLE_THRESHOLD) {
            return StabilityCategory.STABLE;
        } else if (sg > MARGINAL_THRESHOLD) {
            return StabilityCategory.MARGINAL;
        } else if (sg > UNSTABLE_THRESHOLD) {
            return StabilityCategory.UNSTABLE;
        } else {
            return StabilityCategory.SEVERELY_UNSTABLE;
        }
    }

    /**
     * 获取精度修正系数。
     * <p>
     * 稳定性越差，精度越低（散布越大）。
     *
     * @param sg 稳定性因子
     * @return 精度修正系数（1.0 = 无惩罚，>1.0 = 散布增大）
     */
    public static float getInaccuracyModifier(float sg) {
        StabilityCategory category = getStabilityCategory(sg);
        return switch (category) {
            case STABLE -> 1.0f;
            case MARGINAL -> 1.1f;   // +10%散布
            case UNSTABLE -> 2.0f;   // +100%散布（翻滚）
            case SEVERELY_UNSTABLE -> 5.0f; // +400%散布（严重失稳）
        };
    }

    /**
     * 获取伤害修正系数。
     * <p>
     * 不稳定的弹头翻滚时，虽然精度下降，但命中后的杀伤力可能增加
     * （翻滚的弹头在组织中造成更大的空腔）。
     *
     * @param sg 稳定性因子
     * @return 伤害修正系数（1.0 = 无修正）
     */
    public static float getDamageModifier(float sg) {
        StabilityCategory category = getStabilityCategory(sg);
        return switch (category) {
            case STABLE -> 1.0f;
            case MARGINAL -> 1.0f;
            case UNSTABLE -> 1.2f;   // +20%伤害（翻滚增加杀伤）
            case SEVERELY_UNSTABLE -> 0.8f; // -20%伤害（弹头过早解体）
        };
    }

    /**
     * 计算有效弹头长度（英寸）。
     * <p>
     * 如果指定了 bulletLength，直接使用；否则从口径和弹头类型推算。
     * 推算规则：弹头长度 ≈ 口径 × 2.5 × 弹头类型修正
     */
    private static float calculateEffectiveBulletLength(CartridgeType cartridgeType,
                                                         BulletType bulletType,
                                                         float bulletLength) {
        if (bulletLength > 0) {
            // 如果已指定弹头长度（英寸），直接使用
            return bulletLength;
        }
        // 从口径推算：弹头长度 ≈ 口径(mm) × 2.5 × 弹头类型修正 × mm→英寸
        return cartridgeType.bulletDiameter() * 2.5f * bulletType.getBulletLengthModifier() * 0.03937f;
    }

    /**
     * 计算Greenhill推荐缠距（英寸/转）。
     * <p>
     * T = 150 × D² / L
     * 这是使 Sg = 1.0 的临界缠距。
     */
    private static float calculateRecommendedTwistRate(float diameterInches, float lengthInches) {
        if (lengthInches <= 0) return 7f; // 默认1:7
        return 150f * diameterInches * diameterInches / lengthInches;
    }

    /**
     * 稳定性分类枚举。
     */
    public enum StabilityCategory {
        /** 完全稳定：Sg > 1.5 */
        STABLE,
        /** 边缘稳定：1.0 < Sg ≤ 1.5 */
        MARGINAL,
        /** 不稳定：0.5 < Sg ≤ 1.0，弹头翻滚 */
        UNSTABLE,
        /** 严重失稳：Sg ≤ 0.5，弹头可能过早解体 */
        SEVERELY_UNSTABLE
    }
}
