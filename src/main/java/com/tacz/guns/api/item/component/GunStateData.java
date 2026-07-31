package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.ContaminationType;
import com.tacz.guns.api.item.enums.GunCycleState;
import com.tacz.guns.api.item.enums.MalfunctionType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械运行状态数据组件。
 * <p>
 * 对应设计文档：E.4.1 枪机循环状态 + F.4.1 炸膛相关NBT字段 + G.4.1 过热数据扩展
 * <p>
 * 统一管理枪械的运行状态，包括：
 * - 枪机循环状态（状态机）
 * - 枪膛内弹药（具体一发子弹的完整数据，支持哑弹/瞎火/炸膛判定）
 * - 故障类型和清除进度
 * - 过热/烧蚀数据
 * - 枪管异物/枪管损伤
 * - 撞针磨损
 * - 保险状态
 * <p>
 * P0 补充更新：将"是否已上膛"从简单布尔值改为 {@code chamberedRound}，
 * 使枪膛状态能够追溯到具体这一发子弹的详细数据，
 * 为后续哑弹/瞎火/炸膛判定提供数据基础。
 */
public record GunStateData(
        GunCycleState cycleState,
        MalfunctionType malfunctionType,
        float malfunctionClearProgress,
        boolean hasSquibInBarrel,
        float erosionAccumulated,
        int barrelDamageLevel,
        ContaminationType contaminationType,
        float firingPinWear,
        boolean safetyOn,
        /**
         * 枪膛内弹药。
         * <p>
         * P0 补充：替换原有的"是否已上膛"简单布尔值概念。
         * <ul>
         *   <li>非空 = 已上膛，此发子弹的完整数据可用于后续判定</li>
         *   <li>null = 未上膛</li>
         * </ul>
         * 通过此字段可追溯：
         * <ul>
         *   <li>弹药口径 → 判定是否与枪膛匹配</li>
         *   <li>弹壳状态/底火类型 → 判定瞎火概率</li>
         *   <li>装药量/装药偏差 → 判定炸膛概率</li>
         *   <li>弹壳材质 → 判定腐蚀性弹药</li>
         *   <li>弹头类型 → 判定终点弹道效果</li>
         * </ul>
         */
        @Nullable LoadedRound chamberedRound
) {
    /**
     * 创建默认状态数据（空枪膛，无故障）
     */
    public static GunStateData createDefault() {
        return new GunStateData(
                GunCycleState.READY,
                null,  // 无故障
                0.0f,
                false,
                0.0f,
                0,  // 枪管完好
                ContaminationType.NONE,
                0.0f,
                false,  // 保险关闭
                null   // 枪膛为空
        );
    }

    /**
     * 创建默认状态数据（空仓挂机状态）
     */
    public static GunStateData createEmpty() {
        return new GunStateData(
                GunCycleState.EMPTY,
                null,
                0.0f,
                false,
                0.0f,
                0,
                ContaminationType.NONE,
                0.0f,
                false,
                null
        );
    }

    public static final Codec<GunStateData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GunCycleState.CODEC.fieldOf("cycle_state").forGetter(GunStateData::cycleState),
                    MalfunctionType.CODEC.optionalFieldOf("malfunction").forGetter(d ->
                            java.util.Optional.ofNullable(d.malfunctionType())),
                    Codec.FLOAT.fieldOf("malfunction_clear_progress").forGetter(GunStateData::malfunctionClearProgress),
                    Codec.BOOL.fieldOf("has_squib").forGetter(GunStateData::hasSquibInBarrel),
                    Codec.FLOAT.fieldOf("erosion").forGetter(GunStateData::erosionAccumulated),
                    Codec.INT.fieldOf("barrel_damage").forGetter(GunStateData::barrelDamageLevel),
                    ContaminationType.CODEC.fieldOf("contamination").forGetter(GunStateData::contaminationType),
                    Codec.FLOAT.fieldOf("firing_pin_wear").forGetter(GunStateData::firingPinWear),
                    Codec.BOOL.fieldOf("safety").forGetter(GunStateData::safetyOn),
                    LoadedRound.CODEC.optionalFieldOf("chambered_round").forGetter(d ->
                            java.util.Optional.ofNullable(d.chamberedRound()))
            ).apply(instance, (cycle, malfunctionOpt, clearProgress, squib, erosion, barrelDmg,
                               contamination, pinWear, safety, chamberedOpt) ->
                    new GunStateData(cycle, malfunctionOpt.orElse(null), clearProgress, squib,
                            erosion, barrelDmg, contamination, pinWear, safety, chamberedOpt.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, GunStateData> STREAM_CODEC = StreamCodec.composite(
            GunCycleState.STREAM_CODEC, GunStateData::cycleState,
            ByteBufCodecs.optional(MalfunctionType.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.malfunctionType()),
            ByteBufCodecs.FLOAT, GunStateData::malfunctionClearProgress,
            ByteBufCodecs.BOOL, GunStateData::hasSquibInBarrel,
            ByteBufCodecs.FLOAT, GunStateData::erosionAccumulated,
            ByteBufCodecs.INT, GunStateData::barrelDamageLevel,
            ContaminationType.STREAM_CODEC, GunStateData::contaminationType,
            ByteBufCodecs.FLOAT, GunStateData::firingPinWear,
            ByteBufCodecs.BOOL, GunStateData::safetyOn,
            ByteBufCodecs.optional(LoadedRound.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.chamberedRound()),
            (cycle, malfunctionOpt, clearProgress, squib, erosion, barrelDmg,
             contamination, pinWear, safety, chamberedOpt) ->
                    new GunStateData(cycle, malfunctionOpt.orElse(null), clearProgress, squib,
                            erosion, barrelDmg, contamination, pinWear, safety, chamberedOpt.orElse(null))
    );

    // ====== 便捷查询方法 ======

    /**
     * 是否已上膛（枪膛内有弹）
     */
    public boolean hasChamberedRound() {
        return chamberedRound != null;
    }

    /**
     * 是否处于故障状态
     */
    public boolean hasMalfunction() {
        return malfunctionType != null;
    }

    /**
     * 是否可以射击
     * <p>
     * 需同时满足：待发状态 + 无故障 + 保险关闭 + 枪膛内有弹
     */
    public boolean canFire() {
        return cycleState == GunCycleState.READY && !hasMalfunction() && !safetyOn && hasChamberedRound();
    }

    /**
     * 烧蚀对精度的永久性影响
     */
    public float getErosionAccuracyPenalty() {
        if (erosionAccumulated > 100) return 0.50f;  // -50%
        if (erosionAccumulated > 75) return 0.30f;   // -30%
        if (erosionAccumulated > 50) return 0.15f;   // -15%
        if (erosionAccumulated > 25) return 0.05f;   // -5%
        return 0.0f;
    }

    /**
     * 撞针磨损对瞎火概率的贡献
     */
    public float getFiringPinHangfireBonus() {
        return firingPinWear * 0.005f;
    }

    /**
     * 枪膛内弹药的炸膛风险评估。
     * <p>
     * 综合考虑装药量、弹壳状态、枪管损伤、枪管异物等因素。
     *
     * @return 炸膛概率修正（0.0 = 无风险，1.0 = 确定炸膛）
     */
    public float getCatastrophicRiskAssessment() {
        float risk = 0.0f;

        // 枪管异物风险
        risk += contaminationType.getCatastrophicRiskModifier() * 0.5f;

        // 枪管损伤风险
        if (barrelDamageLevel >= 3) risk += 0.4f;
        else if (barrelDamageLevel >= 2) risk += 0.2f;

        // 装药过量风险
        if (chamberedRound != null && chamberedRound.isOvercharged()) {
            risk += 0.3f;
        }

        // 烧蚀风险
        if (erosionAccumulated > 100) risk += 0.2f;

        return Math.min(1.0f, risk);
    }

    // ====== with* 方法 ======

    /**
     * 设置枪机循环状态
     */
    public GunStateData withCycleState(GunCycleState state) {
        return new GunStateData(state, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 设置故障
     */
    public GunStateData withMalfunction(MalfunctionType type) {
        return new GunStateData(GunCycleState.MALFUNCTION, type, 0.0f, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 清除故障
     */
    public GunStateData withMalfunctionCleared() {
        return new GunStateData(GunCycleState.EMPTY, null, 0.0f, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 更新故障清除进度
     */
    public GunStateData withClearProgress(float progress) {
        return new GunStateData(cycleState, malfunctionType, progress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 添加烧蚀
     */
    public GunStateData withErosionAdded(float amount) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated + amount, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 添加撞针磨损
     */
    public GunStateData withFiringPinWear(float amount) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear + amount, safetyOn, chamberedRound);
    }

    /**
     * 设置Squib状态
     */
    public GunStateData withSquib(boolean hasSquib) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquib,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 设置枪管损伤等级
     */
    public GunStateData withBarrelDamage(int level) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, level, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * 设置保险状态
     */
    public GunStateData withSafety(boolean on) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, on, chamberedRound);
    }

    /**
     * 上膛：将弹药装入枪膛。
     * <p>
     * 仅在 EMPTY 状态下可上膛。
     */
    public GunStateData withChamberedRound(@Nullable LoadedRound round) {
        return new GunStateData(
                round != null ? GunCycleState.READY : GunCycleState.EMPTY,
                malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, round);
    }

    /**
     * 击发：清空枪膛弹药。
     * <p>
     * 击发后枪膛为空，状态由调用方根据枪机循环状态决定。
     */
    public GunStateData withChamberedRoundFired() {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, null);
    }

    // ====== P3过热/炸膛/保养扩展方法 ======

    /**
     * P3扩展：射击后烧蚀累积（过热时烧蚀加速）。
     * <p>
     * 烧蚀速率 = erosion_per_shot × (1 + heat_percentage × 2.0)
     * <p>
     * 过热时烧蚀加速（最高3倍）：
     * <ul>
     *   <li>heat 0%: erosion × 1.0（正常）</li>
     *   <li>heat 50%: erosion × 2.0（加倍）</li>
     *   <li>heat 100%: erosion × 3.0（三倍）</li>
     * </ul>
     *
     * @param erosionPerShot  基础烧蚀速率（来自 GunHeatData）
     * @param heatPercentage  当前热量百分比（0.0~1.0）
     * @return 更新后的 GunStateData
     */
    public GunStateData withErosionFromShot(float erosionPerShot, float heatPercentage) {
        float accelerated = erosionPerShot * (1.0f + Math.max(0f, heatPercentage) * 2.0f);
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated + accelerated, barrelDamageLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * P3扩展：炸膛后枪管损伤升级。
     * <p>
     * 根据炸膛严重度升级枪管损伤等级：
     * <ul>
     *   <li>severity 1（轻微）：barrelDamageLevel +1（上限3）</li>
     *   <li>severity 2（中等）：barrelDamageLevel = 3（严重损毁）</li>
     *   <li>severity 3（严重）：barrelDamageLevel = 3（严重损毁，武器报废）</li>
     * </ul>
     *
     * @param severity 炸膛严重度（1=轻微，2=中等，3=严重）
     * @return 更新后的 GunStateData
     */
    public GunStateData withBarrelDamageFromCatastrophic(int severity) {
        int newLevel = switch (severity) {
            case 1 -> Math.min(3, barrelDamageLevel + 1); // 轻微炸膛：损伤+1
            case 2 -> 3;  // 中等炸膛：枪管损毁
            case 3 -> 3;  // 严重炸膛：武器报废
            default -> barrelDamageLevel;
        };
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, newLevel, contaminationType, firingPinWear, safetyOn, chamberedRound);
    }

    /**
     * P3扩展：Cook-off概率（每tick）。
     * <p>
     * 当枪管过热且枪膛内有弹时，膛内弹药可能自燃。
     * <p>
     * 概率模型：cookoff_probability = (heatPercentage - threshold) × 0.001
     * <p>
     * 注意：此方法仅计算概率，不检查闭膛待击条件。
     * 闭膛待击的检查由调用方负责（仅 CLOSED_BOLT/MANUAL_ACTION 受影响）。
     *
     * @param heatPercentage 当前热量百分比（0.0~1.0）
     * @param cookoffThreshold Cook-off阈值（来自 GunHeatData，默认0.85）
     * @return Cook-off概率（0.0~1.0）
     */
    public float getCookoffProbability(float heatPercentage, float cookoffThreshold) {
        if (heatPercentage <= cookoffThreshold) return 0.0f;
        if (!hasChamberedRound()) return 0.0f;
        // 概率随过热程度线性增长
        // heat 85%: 0.0%/tick
        // heat 90%: 0.005%/tick
        // heat 100%: 0.015%/tick
        return (heatPercentage - cookoffThreshold) * 0.001f;
    }

    /**
     * P3扩展：炸膛综合评分（加权判定模型）。
     * <p>
     * 评分模型汇总所有炸膛触发条件，返回综合评分。
     * 综合概率 = min(95%, score × 0.01)
     * <p>
     * 严重度判定：
     * <ul>
     *   <li>score < 5: severity 1（轻微）</li>
     *   <li>5 ≤ score < 20: severity 2（中等）</li>
     *   <li>score ≥ 20: severity 3（严重）</li>
     * </ul>
     *
     * @param heatPercentage   当前热量百分比（0.0~1.0）
     * @param catastrophicHeatThreshold 过热炸膛阈值（来自 GunHeatData）
     * @param toleranceScore   公差评分（0~100，越高越好）
     * @return 炸膛综合评分
     */
    public float getCatastrophicFailureScore(float heatPercentage,
                                              float catastrophicHeatThreshold,
                                              float toleranceScore) {
        float score = 0.0f;

        // 装药过量风险
        if (chamberedRound != null && chamberedRound.isOvercharged()) {
            float overcharge = chamberedRound.getEffectivePowderCharge();
            if (overcharge > 1.5f) {
                score += 10.0f;  // 严重过量装药
            } else if (overcharge > 1.2f) {
                score += 3.0f;   // 过量装药
            }
        }

        // Squib在枪管内
        if (hasSquibInBarrel) {
            score += 80.0f;  // Squib后射击几乎必炸
        }

        // 枪管异物
        score += contaminationType.getCatastrophicRiskModifier() * 3.0f;

        // 极端过热
        if (heatPercentage > catastrophicHeatThreshold) {
            score += (heatPercentage - catastrophicHeatThreshold) * 50.0f;
        }

        // 公差差
        if (toleranceScore < 100) {
            score += (100.0f - toleranceScore) / 100.0f * 5.0f;
        }

        // 枪管损伤
        if (barrelDamageLevel >= 2) score += 10.0f;
        else if (barrelDamageLevel >= 1) score += 3.0f;

        // 烧蚀严重
        if (erosionAccumulated > 100) score += 5.0f;

        return score;
    }

    /**
     * P3扩展：根据炸膛评分确定严重度。
     *
     * @param score 炸膛综合评分
     * @return 严重度（1=轻微，2=中等，3=严重）
     */
    public static int getCatastrophicSeverity(float score) {
        if (score < 5) return 1;
        if (score < 20) return 2;
        return 3;
    }

    /**
     * P3扩展：设置枪管异物类型。
     */
    public GunStateData withContamination(ContaminationType type) {
        return new GunStateData(cycleState, malfunctionType, malfunctionClearProgress, hasSquibInBarrel,
                erosionAccumulated, barrelDamageLevel, type, firingPinWear, safetyOn, chamberedRound);
    }
}
