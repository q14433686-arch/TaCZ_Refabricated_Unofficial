package com.tacz.guns.api.item.ballistics;

import com.google.gson.annotations.SerializedName;

/**
 * 弹道全局配置。
 * <p>
 * 数据驱动：通过JSON配置文件定义全局弹道参数，
 * 允许服主/整合包作者调整弹道手感而不需修改代码。
 * <p>
 * 对应设计文档：C.3 所需道具/机器/工作台清单
 * <p>
 * 使用方式：
 * <pre>
 * // 在代码中读取配置
 * BallisticsConfig config = BallisticsConfig.INSTANCE;
 * float gravity = config.getGravityCoefficient();
 * </pre>
 * <p>
 * 配置文件位置：{@code config/tacz_ballistics.json}
 * <p>
 * 所有数值均为倍率/系数，基于真实物理但经过游戏化调整。
 */
public final class BallisticsConfig {

    /** 全局单例 */
    public static final BallisticsConfig INSTANCE = new BallisticsConfig();

    // ====== 重力与阻力 ======

    /** 重力系数（游戏单位/tick²）。默认0.0，与TACZ原有gravity字段配合 */
    @SerializedName("gravity_coefficient")
    private float gravityCoefficient = 0.0f;

    /** 空气阻力系数。默认0.01，与TACZ原有friction字段配合 */
    @SerializedName("air_drag_coefficient")
    private float airDragCoefficient = 0.01f;

    /** 弹道系数影响系数（0=无影响，1=完全按弹道系数计算阻力） */
    @SerializedName("ballistic_coefficient_weight")
    private float ballisticCoefficientWeight = 0.3f;

    // ====== 初速 ======

    /** 初速整体倍率（全局调整，1.0=原始值） */
    @SerializedName("muzzle_velocity_multiplier")
    private float muzzleVelocityMultiplier = 1.0f;

    /** 枪管长度修正权重（0=无枪管长度影响，1=完全按公式） */
    @SerializedName("barrel_length_weight")
    private float barrelLengthWeight = 1.0f;

    // ====== 膛压与炸膛 ======

    /** 炸膛基础概率（0.001 = 0.1%） */
    @SerializedName("catastrophic_base_rate")
    private float catastrophicBaseRate = 0.001f;

    /** 超压炸膛概率放大系数 */
    @SerializedName("overpressure_catastrophic_amplifier")
    private float overpressureCatastrophicAmplifier = 100f;

    // ====== 稳定性 ======

    /** 是否启用缠距匹配判定 */
    @SerializedName("enable_twist_rate_stability")
    private boolean enableTwistRateStability = true;

    /** 完全稳定阈值 */
    @SerializedName("stability_stable_threshold")
    private float stabilityStableThreshold = 1.5f;

    /** 边缘稳定阈值 */
    @SerializedName("stability_marginal_threshold")
    private float stabilityMarginalThreshold = 1.0f;

    /** 不稳定阈值 */
    @SerializedName("stability_unstable_threshold")
    private float stabilityUnstableThreshold = 0.5f;

    // ====== 穿透与跳弹 ======

    /** 是否启用跳弹系统 */
    @SerializedName("enable_ricochet")
    private boolean enableRicochet = true;

    /** 跳弹最大角度阈值（度） */
    @SerializedName("ricochet_angle_threshold")
    private float ricochetAngleThreshold = 15f;

    /** 跳弹后伤害比例 */
    @SerializedName("ricochet_damage_ratio")
    private float ricochetDamageRatio = 0.3f;

    /** 跳弹后速度比例 */
    @SerializedName("ricochet_velocity_ratio")
    private float ricochetVelocityRatio = 0.5f;

    // ====== 弹壳掉落 ======

    /** 是否启用弹壳掉落 */
    @SerializedName("enable_casing_drop")
    private boolean enableCasingDrop = true;

    /** 弹壳存活时间（tick） */
    @SerializedName("casing_lifetime_ticks")
    private int casingLifetimeTicks = 100;

    /** 弹壳抛出速度 */
    @SerializedName("casing_eject_speed")
    private float casingEjectSpeed = 0.3f;

    // ====== 风偏（进阶可选） ======

    /** 是否启用风偏 */
    @SerializedName("enable_wind")
    private boolean enableWind = false;

    /** 风向更新间隔（tick） */
    @SerializedName("wind_update_interval")
    private int windUpdateInterval = 6000;

    /** 最大风速（m/s） */
    @SerializedName("max_wind_speed")
    private float maxWindSpeed = 5.0f;

    /** 风偏影响系数 */
    @SerializedName("wind_effect_multiplier")
    private float windEffectMultiplier = 0.01f;

    // ====== Getter ======

    public float getGravityCoefficient() { return gravityCoefficient; }
    public float getAirDragCoefficient() { return airDragCoefficient; }
    public float getBallisticCoefficientWeight() { return ballisticCoefficientWeight; }
    public float getMuzzleVelocityMultiplier() { return muzzleVelocityMultiplier; }
    public float getBarrelLengthWeight() { return barrelLengthWeight; }
    public float getCatastrophicBaseRate() { return catastrophicBaseRate; }
    public float getOverpressureCatastrophicAmplifier() { return overpressureCatastrophicAmplifier; }
    public boolean isEnableTwistRateStability() { return enableTwistRateStability; }
    public float getStabilityStableThreshold() { return stabilityStableThreshold; }
    public float getStabilityMarginalThreshold() { return stabilityMarginalThreshold; }
    public float getStabilityUnstableThreshold() { return stabilityUnstableThreshold; }
    public boolean isEnableRicochet() { return enableRicochet; }
    public float getRicochetAngleThreshold() { return ricochetAngleThreshold; }
    public float getRicochetDamageRatio() { return ricochetDamageRatio; }
    public float getRicochetVelocityRatio() { return ricochetVelocityRatio; }
    public boolean isEnableCasingDrop() { return enableCasingDrop; }
    public int getCasingLifetimeTicks() { return casingLifetimeTicks; }
    public float getCasingEjectSpeed() { return casingEjectSpeed; }
    public boolean isEnableWind() { return enableWind; }
    public int getWindUpdateInterval() { return windUpdateInterval; }
    public float getMaxWindSpeed() { return maxWindSpeed; }
    public float getWindEffectMultiplier() { return windEffectMultiplier; }
}
