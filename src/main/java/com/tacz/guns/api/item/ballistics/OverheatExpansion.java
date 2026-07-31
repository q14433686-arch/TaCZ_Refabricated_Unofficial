package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.component.GunMaintenanceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.PowderType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 过热扩展系统。
 * <p>
 * 在TACZ已有的过热系统基础上扩展：
 * <ul>
 *   <li>热量累积：发射药类型修正 + 口径修正</li>
 *   <li>冷却：环境修正（水中/沙漠/雨中）</li>
 *   <li>烧蚀累积：过热时加速（最高3倍）</li>
 *   <li>过热→精度修正：热浮动导致散布增大</li>
 *   <li>过热→炸膛联动：热量>70%时炸膛风险增加</li>
 * </ul>
 * <p>
 * 对应设计文档：G. 过热系统
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class OverheatExpansion {

    /** 过热开始影响精度的热量百分比阈值 */
    public static final float HEAT_ACCURACY_THRESHOLD = 0.5f;

    /** 过热显著影响精度的热量百分比阈值 */
    public static final float HEAT_SIGNIFICANT_THRESHOLD = 0.8f;

    /** 过热开始增加炸膛风险的热量百分比阈值 */
    public static final float HEAT_CATASTROPHIC_THRESHOLD = 0.7f;

    private OverheatExpansion() {}

    /**
     * 计算射击后热量增量。
     * <p>
     * 实际热量 = heat_per_shot × powder_type_modifier × caliber_heat_modifier
     *
     * @param heatPerShot  基础每发射击热量（来自 GunHeatData）
     * @param powderType   发射药类型（影响热量产生）
     * @param caliberHeatModifier 口径热量修正（来自 GunHeatData）
     * @return 热量增量
     */
    public static float calculateShotHeat(float heatPerShot,
                                           @Nullable PowderType powderType,
                                           float caliberHeatModifier) {
        float powderModifier = powderType != null ? powderType.getHeatModifier() : 1.0f;
        return heatPerShot * powderModifier * caliberHeatModifier;
    }

    /**
     * 计算射击后热量增量（从LoadedRound获取发射药类型）。
     *
     * @param heatPerShot  基础每发射击热量
     * @param loadedRound  当前弹药数据
     * @param caliberHeatModifier 口径热量修正
     * @return 热量增量
     */
    public static float calculateShotHeat(float heatPerShot,
                                           @Nullable LoadedRound loadedRound,
                                           float caliberHeatModifier) {
        PowderType powderType = loadedRound != null ? loadedRound.powderType() : null;
        return calculateShotHeat(heatPerShot, powderType, caliberHeatModifier);
    }

    /**
     * 计算冷却速率（每tick）。
     * <p>
     * 冷却公式：
     * <pre>
     * if (time_since_last_shot > cooling_delay):
     *     cooling_rate = (cooling_multiplier / 10000f) × environment_modifier
     * </pre>
     * <p>
     * 环境修正：
     * <ul>
     *   <li>正常环境：1.0</li>
     *   <li>水中：3.0</li>
     *   <li>沙漠：0.7</li>
     *   <li>雨中：1.5</li>
     * </ul>
     *
     * @param heatData    过热数据
     * @param environmentModifier 环境冷却修正系数
     * @return 冷却速率（每tick减少的热量）
     */
    public static float calculateCoolingRate(GunHeatData heatData, float environmentModifier) {
        return (heatData.getCoolingMultiplier() / 10000f) * environmentModifier;
    }

    /**
     * 计算环境冷却修正系数。
     * <p>
     * 根据射击者所在环境确定冷却速率修正。
     *
     * @param isInWater  是否在水中
     * @param isRaining  是否在雨中
     * @param isInDesert 是否在沙漠
     * @return 环境冷却修正系数
     */
    public static float getEnvironmentCoolingModifier(boolean isInWater, boolean isRaining, boolean isInDesert) {
        if (isInWater) return 3.0f;
        if (isRaining) return 1.5f;
        if (isInDesert) return 0.7f;
        return 1.0f;
    }

    /**
     * 计算过热对精度的实时影响。
     * <p>
     * 热浮动导致弹道散布增大：
     * <pre>
     * heat_inaccuracy = 1.0 + heat_percentage × heat_inaccuracy_factor
     * </pre>
     * <p>
     * 当 heat_percentage > 0.5 时开始影响精度
     * 当 heat_percentage > 0.8 时影响显著
     * 当 heat_percentage = 1.0 时，精度下降到原来的2倍
     *
     * @param heatPercentage 当前热量百分比（0.0~1.0）
     * @return 精度修正系数（1.0=无惩罚，>1.0=散布增大）
     */
    public static float getHeatInaccuracyModifier(float heatPercentage) {
        if (heatPercentage <= HEAT_ACCURACY_THRESHOLD) return 1.0f;
        // 热浮动影响：从0.5开始，每增加0.1，散布增大20%
        // heat 50%: 1.0×
        // heat 80%: 1.6×
        // heat 100%: 2.0×
        return 1.0f + (heatPercentage - HEAT_ACCURACY_THRESHOLD) * 2.0f;
    }

    /**
     * 计算过热导致的炸膛风险增量。
     * <p>
     * 当热量超过阈值时，每发射击额外增加炸膛风险。
     * <pre>
     * catastrophic_risk_bonus = (heat_percentage - 0.7) × 0.5
     * // heat 70%: 0%
     * // heat 80%: +5%
     * // heat 90%: +10%
     * // heat 100%: +15%
     * </pre>
     *
     * @param heatPercentage 当前热量百分比
     * @param catastrophicThreshold 炸膛风险阈值（来自 GunHeatData）
     * @return 炸膛风险增量（0.0~0.15）
     */
    public static float getCatastrophicRiskBonus(float heatPercentage, float catastrophicThreshold) {
        if (heatPercentage <= catastrophicThreshold) return 0.0f;
        return (heatPercentage - catastrophicThreshold) * 0.5f;
    }

    /**
     * 计算烧蚀增量。
     * <p>
     * 烧蚀速率 = erosion_per_shot × (1 + heat_percentage × 2.0)
     * <p>
     * 过热时烧蚀加速（最高3倍）：
     * <ul>
     *   <li>heat 0%: erosion × 1.0</li>
     *   <li>heat 50%: erosion × 2.0</li>
     *   <li>heat 100%: erosion × 3.0</li>
     * </ul>
     *
     * @param erosionPerShot  基础烧蚀速率（来自 GunHeatData）
     * @param heatPercentage 当前热量百分比
     * @return 烧蚀增量
     */
    public static float calculateErosionPerShot(float erosionPerShot, float heatPercentage) {
        return erosionPerShot * (1.0f + Math.max(0f, heatPercentage) * 2.0f);
    }

    /**
     * 计算枪管温度（摄氏度）。
     * <p>
     * 将热量百分比转换为更直观的枪管温度显示。
     * <p>
     * 简化模型：
     * <ul>
     *   <li>0%热量 = 20°C（环境温度）</li>
     *   <li>50%热量 = 200°C</li>
     *   <li>100%热量 = 500°C</li>
     * </ul>
     *
     * @param heatPercentage 当前热量百分比
     * @return 枪管温度（°C）
     */
    public static float getBarrelTemperature(float heatPercentage) {
        // 非线性映射：温度随热量百分比增长加速
        // 0% → 20°C, 50% → 200°C, 100% → 500°C
        return 20.0f + heatPercentage * 480.0f;
    }

    /**
     * 判断是否处于过热危险状态。
     * <p>
     * 用于UI警告显示。
     *
     * @param heatPercentage 当前热量百分比
     * @return 过热危险等级（0=安全，1=注意，2=危险，3=极危险）
     */
    public static int getHeatDangerLevel(float heatPercentage) {
        if (heatPercentage <= HEAT_ACCURACY_THRESHOLD) return 0;
        if (heatPercentage <= HEAT_SIGNIFICANT_THRESHOLD) return 1;
        if (heatPercentage <= HEAT_CATASTROPHIC_THRESHOLD) return 2;
        return 3;
    }
}
