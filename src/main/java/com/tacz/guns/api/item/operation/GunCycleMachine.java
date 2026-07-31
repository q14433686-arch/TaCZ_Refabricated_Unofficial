package com.tacz.guns.api.item.operation;

import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.GunWearData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.GunCycleState;
import com.tacz.guns.api.item.enums.MalfunctionType;
import org.jetbrains.annotations.Nullable;

/**
 * 枪机循环状态机。
 * <p>
 * P1 收尾：完整集成模块化耐久系统。
 * <ul>
 *   <li>每次射击循环按文档 I.2.1 磨损速率表独立扣减7个部件的耐久</li>
 *   <li>overall_reliability 乘以 (2.0 - overall_reliability) 放大系数接入卡壳判定</li>
 *   <li>overall_accuracy 供后续精度修正使用</li>
 *   <li>弹簧疲劳与耐久系统共用同一套加权公式，无双重计算</li>
 * </ul>
 */
public final class GunCycleMachine {

    private GunCycleMachine() {}

    /**
     * 执行一次完整的射击循环（自装填枪械）。
     * <p>
     * 循环：FIRING → UNLOCKING → EXTRACTING → EJECTING → FEEDING → LOCKING → READY
     *
     * @param state      当前枪械状态
     * @param feedDevice 供弹具数据（可以为 null）
     * @param wearData   模块化耐久数据
     * @return 循环后的新状态（含更新后的 GunWearData）
     */
    public static CycleResult fireAutoCycle(GunStateData state, @Nullable FeedDeviceData feedDevice,
                                            GunWearData wearData) {
        // 前置检查
        if (state.cycleState() != GunCycleState.READY) {
            return CycleResult.fail("Cannot fire: not in READY state");
        }
        if (!state.hasChamberedRound()) {
            return CycleResult.fail("Cannot fire: no chambered round");
        }

        // 从耐久系统计算加权可靠性/精度
        float overallReliability = wearData.calculateOverallReliability();
        float overallAccuracy = wearData.calculateOverallAccuracy();

        // 将 overall_reliability 放大为卡壳概率系数
        // reliability = 1.0 → 放大系数 = 1.0 (无额外影响)
        // reliability = 0.5 → 放大系数 = 1.5 (卡壳概率增加 50%)
        // reliability = 0.1 → 放大系数 = 1.9 (卡壳概率接近翻倍)
        // reliability = 0.0 → 放大系数 = 2.0 (卡壳概率翻倍)
        float malfunctionAmplifier = 2.0f - overallReliability;

        // 传入原始 reliabilityModifier（来自公差/保养等），结合耐久系统
        float effectiveReliability = overallReliability;

        GunStateData currentState = state.withCycleState(GunCycleState.FIRING);

        // === FIRING: 击发 ===
        LoadedRound chamberedRound = currentState.chamberedRound();

        // 哑弹/不发火概率
        float misfireChance = calculateMisfireChance(chamberedRound, currentState, effectiveReliability) * malfunctionAmplifier;
        if (Math.random() < misfireChance) {
            // 扣减扳机组磨损（扣了扳机但没击发）
            GunWearData updatedWear = wearData.withShootWear(false, false);
            return CycleResult.malfunction(currentState, MalfunctionType.MISFIRE, updatedWear);
        }

        // 炸膛风险评估
        float catastrophicRisk = currentState.getCatastrophicRiskAssessment();
        if (chamberedRound.isOvercharged() && Math.random() < catastrophicRisk) {
            GunWearData updatedWear = wearData.withShootWear(true, chamberedRound.isCorrosive());
            return CycleResult.malfunction(currentState, MalfunctionType.SQUIB, updatedWear);
        }

        // 减装药 → Squib
        if (chamberedRound.isUndercharged() && Math.random() < 0.3f * malfunctionAmplifier) {
            GunWearData updatedWear = wearData.withShootWear(false, chamberedRound.isCorrosive());
            return CycleResult.malfunction(
                    currentState.withSquib(true).withChamberedRoundFired(),
                    MalfunctionType.SQUIB, updatedWear);
        }

        // 击发成功，清空枪膛
        currentState = currentState.withChamberedRoundFired();

        // === 射击耐久消耗（文档 I.2.1） ===
        GunWearData currentWear = wearData.withShootWear(
                chamberedRound.isOvercharged(), chamberedRound.isCorrosive());

        // === UNLOCKING → EXTRACTING: 抽壳 ===
        currentState = currentState.withCycleState(GunCycleState.UNLOCKING);
        currentState = currentState.withCycleState(GunCycleState.EXTRACTING);

        LoadedRound spentCase = chamberedRound;
        float extractFailChance = calculateExtractFailChance(chamberedRound, effectiveReliability) * malfunctionAmplifier;
        if (Math.random() < extractFailChance) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_EXTRACT, currentWear);
        }

        // === EJECTING: 抛壳 ===
        currentState = currentState.withCycleState(GunCycleState.EJECTING);

        FeedResult.EjectResult ejectResult = FeedOperation.ejectCase(spentCase, effectiveReliability);
        if (!ejectResult.isSuccess()) {
            return CycleResult.malfunction(currentState, ejectResult.malfunctionType(), currentWear);
        }

        // === FEEDING: 从供弹具取弹 ===
        currentState = currentState.withCycleState(GunCycleState.FEEDING);

        if (feedDevice == null || feedDevice.isEmpty()) {
            return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, currentWear, ejectResult.ejectedCase());
        }

        FeedResult.StripResult stripResult = FeedOperation.stripNextRound(feedDevice, effectiveReliability);
        if (!stripResult.isSuccess()) {
            if (stripResult.malfunctionType() == null) {
                return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, currentWear, ejectResult.ejectedCase());
            }
            return CycleResult.malfunction(currentState, stripResult.malfunctionType(), currentWear);
        }

        // 推弹入膛
        LoadedRound nextRound = stripResult.strippedRound();
        FeedResult.ChamberResult chamberResult = FeedOperation.chamberRound(currentState, nextRound);
        if (!chamberResult.isSuccess()) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_FEED, currentWear);
        }
        currentState = chamberResult.updatedState();
        FeedDeviceData updatedDevice = stripResult.updatedDevice();

        // 供弹循环消耗弹匣弹簧
        currentWear = currentWear.withMagazineSpringWear();

        // === LOCKING → READY ===
        currentState = currentState.withCycleState(GunCycleState.LOCKING);
        currentState = currentState.withCycleState(GunCycleState.READY);

        return CycleResult.ok(currentState, updatedDevice, currentWear, ejectResult.ejectedCase());
    }

    /**
     * 执行手动拉栓循环（栓动枪/泵动枪）。
     */
    public static CycleResult manualBoltCycle(GunStateData state, @Nullable FeedDeviceData feedDevice,
                                              GunWearData wearData) {
        GunStateData currentState = state;
        LoadedRound ejectedCase = null;
        float overallReliability = wearData.calculateOverallReliability();
        float malfunctionAmplifier = 2.0f - overallReliability;

        if (!GunCycleTransition.isTransitionValid(currentState.cycleState(), GunCycleState.BOLT_OPEN)) {
            return CycleResult.fail("Cannot bolt from state: " + currentState.cycleState());
        }

        currentState = currentState.withCycleState(GunCycleState.BOLT_OPEN);

        // 如果膛内有弹壳，先抽壳抛壳
        if (currentState.hasChamberedRound()) {
            float extractFailChance = calculateExtractFailChance(currentState.chamberedRound(), overallReliability) * malfunctionAmplifier;
            if (Math.random() < extractFailChance) {
                return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_EXTRACT, wearData);
            }
            ejectedCase = currentState.chamberedRound();
            currentState = currentState.withChamberedRoundFired();

            FeedResult.EjectResult ejectResult = FeedOperation.ejectCase(ejectedCase, overallReliability);
            if (!ejectResult.isSuccess()) {
                return CycleResult.malfunction(currentState, ejectResult.malfunctionType(), wearData);
            }
            ejectedCase = ejectResult.ejectedCase();
        }

        // === FEEDING ===
        currentState = currentState.withCycleState(GunCycleState.FEEDING);

        if (feedDevice == null || feedDevice.isEmpty()) {
            return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, wearData, ejectedCase);
        }

        FeedResult.StripResult stripResult = FeedOperation.stripNextRound(feedDevice, overallReliability);
        if (!stripResult.isSuccess()) {
            if (stripResult.malfunctionType() == null) {
                return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, wearData, ejectedCase);
            }
            return CycleResult.malfunction(currentState, stripResult.malfunctionType(), wearData);
        }

        FeedResult.ChamberResult chamberResult = FeedOperation.chamberRound(currentState, stripResult.strippedRound());
        if (!chamberResult.isSuccess()) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_FEED, wearData);
        }
        currentState = chamberResult.updatedState();

        // 供弹循环消耗弹匣弹簧
        GunWearData currentWear = wearData.withMagazineSpringWear();

        currentState = currentState.withCycleState(GunCycleState.LOCKING);
        currentState = currentState.withCycleState(GunCycleState.READY);

        return CycleResult.ok(currentState, stripResult.updatedDevice(), currentWear, ejectedCase);
    }

    /**
     * 清除故障。
     */
    public static GunStateData clearMalfunction(GunStateData state) {
        if (state.cycleState() != GunCycleState.MALFUNCTION) {
            return state;
        }
        return state.withMalfunctionCleared();
    }

    // ====== 概率计算 ======

    private static float calculateMisfireChance(LoadedRound round, GunStateData state, float reliabilityModifier) {
        float chance = 0.001f; // 基础 0.1%

        chance += state.getFiringPinHangfireBonus();

        if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.DEPORMED) {
            chance += 0.05f;
        } else if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CORRODED) {
            chance += 0.03f;
        }

        if (round.isUndercharged()) {
            chance += 0.10f;
        }

        chance *= (1.0f - reliabilityModifier * 0.5f);

        return chance;
    }

    private static float calculateExtractFailChance(LoadedRound round, float reliabilityModifier) {
        float chance = 0.005f;

        if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CRACKED) {
            chance += 0.15f;
        } else if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.DEPORMED) {
            chance += 0.25f;
        } else if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CORRODED) {
            chance += 0.20f;
        }

        if (round.isCorrosive()) {
            chance += 0.05f;
        }

        chance *= (1.0f - reliabilityModifier);

        return chance;
    }

    // ====== 循环结果 ======

    /**
     * 射击循环结果（P1 收尾：携带 GunWearData）。
     */
    public record CycleResult(
            boolean success,
            @Nullable GunStateData state,
            @Nullable FeedDeviceData updatedDevice,
            @Nullable GunWearData updatedWear,
            @Nullable LoadedRound ejectedCase,
            @Nullable MalfunctionType malfunctionType,
            @Nullable String failureReason
    ) {
        /** 成功 */
        public static CycleResult ok(GunStateData state, @Nullable FeedDeviceData updatedDevice,
                                     @Nullable GunWearData updatedWear, @Nullable LoadedRound ejectedCase) {
            return new CycleResult(true, state, updatedDevice, updatedWear, ejectedCase, null, null);
        }

        /** 故障 */
        public static CycleResult malfunction(GunStateData state, MalfunctionType type,
                                              @Nullable GunWearData updatedWear) {
            return new CycleResult(false, state.withMalfunction(type), null, updatedWear, null, type, null);
        }

        /** 失败（前置条件不满足） */
        public static CycleResult fail(String reason) {
            return new CycleResult(false, null, null, null, null, null, reason);
        }
    }
}
