package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.cartridge.CartridgeType;
import com.tacz.guns.api.item.cartridge.CartridgeTypeManager;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.PowderType;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 综合弹道计算器。
 * <p>
 * 整合内弹道、外弹道、终端弹道的统一接口。
 * <p>
 * 使用方式：
 * <pre>
 * // 1. 构建弹道上下文
 * BallisticContext ctx = BallisticContext.builder()
 *     .bulletData(bulletData)
 *     .cartridgeType(cartridgeType)
 *     .loadedRound(loadedRound)
 *     .barrelLength(415)
 *     .twistRate(7)
 *     .build();
 *
 * // 2. 获取计算结果
 * float muzzleVelocity = ctx.getMuzzleVelocity();
 * float peakPressure = ctx.getPeakPressure();
 * float stabilityFactor = ctx.getStabilityFactor();
 * float inaccuracyModifier = ctx.getInaccuracyModifier();
 * </pre>
 * <p>
 * 对应设计文档：C. 弹道与精度系统
 * <p>
 * 设计原则：
 * <ul>
 *   <li>所有衍生值在运行时计算，不存储在数据中</li>
 *   <li>CartridgeType只提供物理规格常量，不包含伤害/初速/射程</li>
 *   <li>AmmoData是唯一事实来源，LoadedRound是快照</li>
 * </ul>
 */
public class BallisticCalculator {

    private BallisticCalculator() {}

    /**
     * 一站式弹道计算：给定所有输入，返回完整的弹道结果。
     *
     * @param bulletData    枪包定义的弹道数据
     * @param cartridgeType 口径规格（可为null，此时使用默认值）
     * @param loadedRound   单发弹药数据
     * @param barrelLength  实际枪管长度（mm）
     * @param twistRate     枪管缠距（英寸/转）
     * @param bulletLength  弹头长度（英寸，0=自动推算）
     * @return 弹道计算结果
     */
    public static BallisticResult calculate(@Nullable BulletData bulletData,
                                             @Nullable CartridgeType cartridgeType,
                                             @Nullable LoadedRound loadedRound,
                                             int barrelLength,
                                             int twistRate,
                                             float bulletLength) {
        // 初速
        float muzzleVelocity = MuzzleVelocityCalculator.calculateMuzzleVelocity(
                bulletData, cartridgeType, loadedRound, barrelLength);

        // 发射药燃速匹配修正
        float burnRateMatchFactor = 1.0f;
        float burnRateMismatchCatastrophicProb = 0f;
        if (loadedRound != null) {
            burnRateMatchFactor = MuzzleVelocityCalculator.calculateBurnRateMatchFactor(
                    loadedRound.powderType(), barrelLength);
            burnRateMismatchCatastrophicProb = MuzzleVelocityCalculator.getBurnRateMismatchCatastrophicProbability(
                    loadedRound.powderType(), barrelLength);
            muzzleVelocity *= burnRateMatchFactor;
        }

        // 膛压
        float peakPressure = ChamberPressure.calculatePeakPressure(
                cartridgeType, loadedRound, barrelLength);

        // 稳定性
        float stabilityFactor = StabilityCalculator.calculateStabilityFactor(
                cartridgeType, loadedRound, twistRate, bulletLength);

        // 精度修正
        float inaccuracyModifier = StabilityCalculator.getInaccuracyModifier(stabilityFactor);

        // 伤害修正
        float stabilityDamageModifier = StabilityCalculator.getDamageModifier(stabilityFactor);

        // 炸膛概率
        float catastrophicProb = 0f;
        if (loadedRound != null) {
            catastrophicProb = ChamberPressure.calculateCatastrophicFailureProbability(
                    peakPressure, cartridgeType, 1.0f);
            catastrophicProb += burnRateMismatchCatastrophicProb;
        }

        // 超压判定
        boolean isOverPressure = ChamberPressure.isOverPressure(peakPressure, cartridgeType);
        float overPressureRatio = ChamberPressure.getOverPressureRatio(peakPressure, cartridgeType);

        // 积碳速率
        float carbonFoulingRate = loadedRound != null ? loadedRound.powderType().getCarbonFoulingRate() : 0.05f;

        // 烧蚀速率
        float erosionRate = loadedRound != null ? loadedRound.powderType().getErosionRate() : 1.0f;

        // 热量产生
        float heatGenerated = loadedRound != null ? loadedRound.powderType().getHeatModifier() : 1.0f;

        // 枪口焰等级
        int muzzleFlashLevel = loadedRound != null ? loadedRound.powderType().getMuzzleFlashLevel() : 1;

        // 烟雾等级
        int smokeLevel = loadedRound != null ? loadedRound.powderType().getSmokeLevel() : 0;

        // 腐蚀性判定
        boolean isCorrosive = loadedRound != null && loadedRound.isCorrosive();

        return new BallisticResult(
                muzzleVelocity,
                peakPressure,
                stabilityFactor,
                inaccuracyModifier,
                stabilityDamageModifier,
                catastrophicProb,
                isOverPressure,
                overPressureRatio,
                burnRateMatchFactor,
                carbonFoulingRate,
                erosionRate,
                heatGenerated,
                muzzleFlashLevel,
                smokeLevel,
                isCorrosive
        );
    }

    /**
     * 从口径ID解析CartridgeType并计算弹道。
     * <p>
     * 便捷方法，自动从 {@link CartridgeTypeManager} 查询口径规格。
     *
     * @param cartridgeTypeId 口径标识符
     * @param bulletData      枪包定义的弹道数据
     * @param loadedRound     单发弹药数据
     * @param barrelLength    实际枪管长度（mm）
     * @param twistRate       枪管缠距（英寸/转）
     * @param bulletLength    弹头长度（英寸，0=自动推算）
     * @return 弹道计算结果
     */
    public static BallisticResult calculateFromId(@Nullable Identifier cartridgeTypeId,
                                                   @Nullable BulletData bulletData,
                                                   @Nullable LoadedRound loadedRound,
                                                   int barrelLength,
                                                   int twistRate,
                                                   float bulletLength) {
        CartridgeType cartridgeType = null;
        if (cartridgeTypeId != null) {
            cartridgeType = CartridgeTypeManager.getCartridgeType(cartridgeTypeId).orElse(null);
        }
        return calculate(bulletData, cartridgeType, loadedRound, barrelLength, twistRate, bulletLength);
    }

    /**
     * 弹道计算结果。
     * <p>
     * 不可变记录，包含所有弹道相关的衍生值。
     * 每次射击时重新计算，不持久化。
     */
    public record BallisticResult(
            /** 初速（游戏单位/tick） */
            float muzzleVelocity,
            /** 峰值膛压（MPa） */
            float peakPressure,
            /** 弹头稳定性因子（Sg） */
            float stabilityFactor,
            /** 精度修正系数（1.0=无惩罚，>1.0=散布增大） */
            float inaccuracyModifier,
            /** 稳定性伤害修正系数（1.0=无修正） */
            float stabilityDamageModifier,
            /** 炸膛概率（每发） */
            float catastrophicProbability,
            /** 是否超压 */
            boolean isOverPressure,
            /** 超压比例（0.0=安全，1.0=超压100%） */
            float overPressureRatio,
            /** 发射药燃速匹配修正因子 */
            float burnRateMatchFactor,
            /** 积碳速率（每发射击） */
            float carbonFoulingRate,
            /** 烧蚀速率（枪管磨损倍率） */
            float erosionRate,
            /** 热量产生系数 */
            float heatGenerated,
            /** 枪口焰等级（0=无，3=大） */
            int muzzleFlashLevel,
            /** 烟雾等级（0=无，2=大） */
            int smokeLevel,
            /** 是否为腐蚀性弹药 */
            boolean isCorrosive
    ) {}
}
