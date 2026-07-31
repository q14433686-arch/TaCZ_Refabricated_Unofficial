package com.tacz.guns.resource.pojo.data.gun;

import com.google.gson.annotations.SerializedName;

public class GunHeatData {

    @SerializedName("max")
    private float heatMax = 100f;

    @SerializedName("per_shot")
    private float heatPerShot = 3f;

    @SerializedName("cooling_multiplier")
    private float coolingMultiplier = 1f;

    @SerializedName("cooling_delay")
    private long coolingDelay = 1000L; //ms

    @SerializedName("over_heat_time")
    private long overHeatTime = 3000L; //ms

    @SerializedName("min_inaccuracy")
    private float minInaccuracy = 1f;

    @SerializedName("max_inaccuracy")
    private float maxInaccuracy = 1f;

    @SerializedName("min_rpm_mod")
    private float minRpmMod = 1f;

    @SerializedName("max_rpm_mod")
    private float maxRpmMod = 1f;

    // ====== P3过热扩展字段 ======

    /**
     * P3扩展：每发射击的烧蚀增量。
     * <p>
     * 烧蚀是不可逆的永久性损伤：发射药高温气体和弹头摩擦共同作用，
     * 使枪管喉部钢材逐渐被侵蚀，导致精度持续下降。
     * <p>
     * 实际烧蚀速率 = erosion_per_shot × (1 + heat_percentage × 2.0)
     * 过热时烧蚀加速（最高3倍）。
     */
    @SerializedName("erosion_per_shot")
    private float erosionPerShot = 0.001f;

    /**
     * P3扩展：Cook-off触发的热量百分比阈值。
     * <p>
     * 当 heat_percentage > cookoff_threshold 且枪膛内有弹时，
     * 膛内弹药可能自燃（Cook-off）。
     * <p>
     * 仅影响闭膛待击武器（CLOSED_BOLT / MANUAL_ACTION），
     * 开膛待击武器不受影响。
     */
    @SerializedName("cookoff_threshold")
    private float cookoffThreshold = 0.85f;

    /**
     * P3扩展：环境冷却修正系数。
     * <p>
     * 不同环境对冷却速率的影响：
     * <ul>
     *   <li>正常环境：此值 × 1.0</li>
     *   <li>水中：此值 × 3.0</li>
     *   <li>沙漠：此值 × 0.7</li>
     *   <li>雨中：此值 × 1.5</li>
     * </ul>
     * 此值用于乘以基础冷却速率，再乘以环境修正系数。
     */
    @SerializedName("environment_cooling_modifier")
    private float environmentCoolingModifier = 1.0f;

    /**
     * P3扩展：口径热量修正系数。
     * <p>
     * 大口径弹药产生更多热量：
     * <ul>
     *   <li>手枪弹（9mm/.45）：0.5</li>
     *   <li>步枪弹（5.56/7.62）：1.0</li>
     *   <li>马格南弹（.50 BMG）：1.5</li>
     * </ul>
     * 实际热量 = heat_per_shot × powder_type_modifier × caliber_heat_modifier
     */
    @SerializedName("caliber_heat_modifier")
    private float caliberHeatModifier = 1.0f;

    /**
     * P3扩展：过热导致炸膛风险的阈值。
     * <p>
     * 当 heat_percentage > catastrophic_heat_threshold 时，
     * 每发射击额外增加炸膛风险。
     * <p>
     * 炸膛风险增量 = (heat_percentage - catastrophic_heat_threshold) × 0.5
     */
    @SerializedName("catastrophic_heat_threshold")
    private float catastrophicHeatThreshold = 0.7f;

    public long getCoolingDelay() {
        return coolingDelay;
    }

    public float getHeatMax() {
        return heatMax;
    }

    public float getHeatPerShot() {
        return heatPerShot;
    }

    public long getOverHeatTime() {
        return overHeatTime;
    }

    public float getMinInaccuracy() {
        return minInaccuracy;
    }

    public float getMaxInaccuracy() {
        return maxInaccuracy;
    }

    public float getCoolingMultiplier() {
        return coolingMultiplier;
    }

    public float getMinRpmMod() {
        return minRpmMod;
    }

    public float getMaxRpmMod() {
        return maxRpmMod;
    }

    // P3扩展：getter

    public float getErosionPerShot() {
        return erosionPerShot;
    }

    public float getCookoffThreshold() {
        return cookoffThreshold;
    }

    public float getEnvironmentCoolingModifier() {
        return environmentCoolingModifier;
    }

    public float getCaliberHeatModifier() {
        return caliberHeatModifier;
    }

    public float getCatastrophicHeatThreshold() {
        return catastrophicHeatThreshold;
    }
}
