package com.tacz.guns.api.item.operation;

import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.ActionType;
import com.tacz.guns.api.item.enums.GunCycleState;
import com.tacz.guns.api.item.enums.MalfunctionType;
import org.jetbrains.annotations.Nullable;

/**
 * 枪机循环状态机。
 * <p>
 * 状态机的每次转移，就是按顺序调用供弹原语：
 * <pre>
 * "拉栓上膛" = extractFromChamber (如果膛里有壳先抽出)
 *           → ejectCase (抛出去)
 *           → stripNextRound (从弹匣取下一发)
 *           → chamberRound (推进膛)
 *           → 状态变为 READY
 * </pre>
 * <p>
 * 每一步都可能失败 → 失败就转移到对应的 MalfunctionType 状态。
 * <p>
 * 对应设计文档：E.2.1 完整枪机循环状态机 & P1 验收标准
 */
public final class GunCycleMachine {

    private GunCycleMachine() {}

    /**
     * 执行一次完整的射击循环（自装填枪械）。
     * <p>
     * 循环：FIRING → UNLOCKING → EXTRACTING → EJECTING → FEEDING → LOCKING → READY
     * <p>
     * 每个状态转换由对应的供弹原语驱动。原语失败时进入 MALFUNCTION 状态。
     *
     * @param state              当前枪械状态
     * @param feedDevice         供弹具数据（可以为 null 表示内置弹仓）
     * @param reliabilityModifier 综合可靠性修正
     * @return 循环后的新状态
     */
    public static CycleResult fireAutoCycle(GunStateData state, @Nullable FeedDeviceData feedDevice,
                                            float reliabilityModifier) {
        // 前置检查：必须处于 READY 状态
        if (state.cycleState() != GunCycleState.READY) {
            return CycleResult.fail("Cannot fire: not in READY state");
        }
        // 前置检查：膛内必须有弹
        if (!state.hasChamberedRound()) {
            return CycleResult.fail("Cannot fire: no chambered round");
        }

        GunStateData currentState = state.withCycleState(GunCycleState.FIRING);

        // === FIRING: 击发 ===
        LoadedRound chamberedRound = currentState.chamberedRound();
        // 击发判定：瞎火/哑弹概率
        float misfireChance = calculateMisfireChance(chamberedRound, currentState, reliabilityModifier);
        if (Math.random() < misfireChance) {
            // 不发火
            return CycleResult.malfunction(currentState, MalfunctionType.MISFIRE);
        }

        // 装药过量 → 炸膛风险评估
        float catastrophicRisk = currentState.getCatastrophicRiskAssessment();
        if (chamberedRound.isOvercharged() && Math.random() < catastrophicRisk) {
            // 炸膛！
            return CycleResult.malfunction(currentState, MalfunctionType.SQUIB);
        }

        // 减装药 → Squib 弹头卡管风险
        if (chamberedRound.isUndercharged() && Math.random() < 0.3f) {
            return CycleResult.malfunction(
                    currentState.withSquib(true).withChamberedRoundFired(),
                    MalfunctionType.SQUIB);
        }

        // 击发成功，清空枪膛
        currentState = currentState.withChamberedRoundFired();

        // === UNLOCKING → EXTRACTING: 抽壳 ===
        currentState = currentState.withCycleState(GunCycleState.UNLOCKING);
        currentState = currentState.withCycleState(GunCycleState.EXTRACTING);

        FeedResult.ExtractResult extractResult = FeedOperation.extractFromChamber(currentState, reliabilityModifier);
        // 注意：此时 chamberedRound 已经被清空了，但 extractFromChamber 需要检查 chamberedRound
        // 实际上在击发后 chamberedRound 已经清空，所以抽壳操作需要特殊处理
        // 修正：击发后弹壳应该留在"待抽壳"状态，我们在 GunStateData 中用一个临时字段来跟踪
        // 但为了简化，我们直接用 extractedCase 来跟踪

        LoadedRound spentCase = chamberedRound; // 击发后的弹壳就是刚才 chamberedRound 的弹壳
        // 抽壳失败判定（基于弹壳状态）
        float extractFailChance = calculateExtractFailChance(chamberedRound, reliabilityModifier);
        if (Math.random() < extractFailChance) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_EXTRACT);
        }

        // === EJECTING: 抛壳 ===
        currentState = currentState.withCycleState(GunCycleState.EJECTING);

        FeedResult.EjectResult ejectResult = FeedOperation.ejectCase(spentCase, reliabilityModifier);
        if (!ejectResult.isSuccess()) {
            return CycleResult.malfunction(currentState, ejectResult.malfunctionType());
        }

        // === FEEDING: 从供弹具取弹 ===
        currentState = currentState.withCycleState(GunCycleState.FEEDING);

        if (feedDevice == null || feedDevice.isEmpty()) {
            // 弹匣空，进入 EMPTY 状态
            return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, ejectResult.ejectedCase());
        }

        FeedResult.StripResult stripResult = FeedOperation.stripNextRound(feedDevice, reliabilityModifier);
        if (!stripResult.isSuccess()) {
            if (stripResult.malfunctionType() == null) {
                // 供弹具为空，不是故障
                return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, ejectResult.ejectedCase());
            }
            return CycleResult.malfunction(currentState, stripResult.malfunctionType());
        }

        // 推弹入膛
        LoadedRound nextRound = stripResult.strippedRound();
        FeedResult.ChamberResult chamberResult = FeedOperation.chamberRound(currentState, nextRound);
        if (!chamberResult.isSuccess()) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_FEED);
        }
        currentState = chamberResult.updatedState();
        FeedDeviceData updatedDevice = stripResult.updatedDevice();

        // === LOCKING → READY ===
        currentState = currentState.withCycleState(GunCycleState.LOCKING);
        currentState = currentState.withCycleState(GunCycleState.READY);

        return CycleResult.ok(currentState, updatedDevice, ejectResult.ejectedCase());
    }

    /**
     * 执行手动拉栓循环（栓动枪/泵动枪）。
     * <p>
     * 循环：当前状态 → BOLT_OPEN → FEEDING → LOCKING → READY
     * <p>
     * 如果膛内有弹壳，先抽壳抛壳，再从供弹具取弹入膛。
     *
     * @param state              当前枪械状态
     * @param feedDevice         供弹具数据
     * @param reliabilityModifier 综合可靠性修正
     * @return 循环后的新状态
     */
    public static CycleResult manualBoltCycle(GunStateData state, @Nullable FeedDeviceData feedDevice,
                                              float reliabilityModifier) {
        GunStateData currentState = state;
        LoadedRound ejectedCase = null;

        // 验证转换合法性
        if (!GunCycleTransition.isTransitionValid(currentState.cycleState(), GunCycleState.BOLT_OPEN)) {
            return CycleResult.fail("Cannot bolt from state: " + currentState.cycleState());
        }

        // === BOLT_OPEN: 拉栓 ===
        currentState = currentState.withCycleState(GunCycleState.BOLT_OPEN);

        // 如果膛内有弹壳，先抽壳抛壳
        if (currentState.hasChamberedRound()) {
            // 抽壳
            float extractFailChance = calculateExtractFailChance(currentState.chamberedRound(), reliabilityModifier);
            if (Math.random() < extractFailChance) {
                return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_EXTRACT);
            }
            ejectedCase = currentState.chamberedRound();
            currentState = currentState.withChamberedRoundFired();

            // 抛壳
            FeedResult.EjectResult ejectResult = FeedOperation.ejectCase(ejectedCase, reliabilityModifier);
            if (!ejectResult.isSuccess()) {
                return CycleResult.malfunction(currentState, ejectResult.malfunctionType());
            }
            ejectedCase = ejectResult.ejectedCase();
        }

        // === FEEDING: 从供弹具取弹 ===
        currentState = currentState.withCycleState(GunCycleState.FEEDING);

        if (feedDevice == null || feedDevice.isEmpty()) {
            // 弹匣空，进入 EMPTY
            return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, ejectedCase);
        }

        FeedResult.StripResult stripResult = FeedOperation.stripNextRound(feedDevice, reliabilityModifier);
        if (!stripResult.isSuccess()) {
            if (stripResult.malfunctionType() == null) {
                return CycleResult.ok(currentState.withCycleState(GunCycleState.EMPTY), null, ejectedCase);
            }
            return CycleResult.malfunction(currentState, stripResult.malfunctionType());
        }

        // 推弹入膛
        FeedResult.ChamberResult chamberResult = FeedOperation.chamberRound(currentState, stripResult.strippedRound());
        if (!chamberResult.isSuccess()) {
            return CycleResult.malfunction(currentState, MalfunctionType.FAILURE_TO_FEED);
        }
        currentState = chamberResult.updatedState();

        // === LOCKING → READY ===
        currentState = currentState.withCycleState(GunCycleState.LOCKING);
        currentState = currentState.withCycleState(GunCycleState.READY);

        return CycleResult.ok(currentState, stripResult.updatedDevice(), ejectedCase);
    }

    /**
     * 清除故障。
     * <p>
     * MALFUNCTION → EMPTY
     *
     * @param state 当前枪械状态
     * @return 清除后的新状态
     */
    public static GunStateData clearMalfunction(GunStateData state) {
        if (state.cycleState() != GunCycleState.MALFUNCTION) {
            return state;
        }
        return state.withMalfunctionCleared();
    }

    // ====== 概率计算 ======

    /**
     * 计算哑弹/不发火概率。
     * <p>
     * 受以下因素影响：
     * - 底火类型（Berdan 底火更可靠）
     * - 弹壳状态（变形/锈蚀影响底火对齐）
     * - 撞针磨损
     * - 装药量（减装药可能不点火）
     */
    private static float calculateMisfireChance(LoadedRound round, GunStateData state, float reliabilityModifier) {
        float chance = 0.001f; // 基础 0.1%

        // 撞针磨损
        chance += state.getFiringPinHangfireBonus();

        // 弹壳状态影响底火对齐
        if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.DEPORMED) {
            chance += 0.05f;
        } else if (round.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CORRODED) {
            chance += 0.03f;
        }

        // 减装药可能不点火
        if (round.isUndercharged()) {
            chance += 0.10f;
        }

        // 可靠性修正
        chance *= (1.0f - reliabilityModifier * 0.5f);

        return chance;
    }

    /**
     * 计算抽壳失败概率。
     */
    private static float calculateExtractFailChance(LoadedRound round, float reliabilityModifier) {
        float chance = 0.005f; // 基础 0.5%

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
     * 射击循环结果。
     *
     * @param success        是否成功
     * @param state          更新后的枪械状态
     * @param updatedDevice  更新后的供弹具数据（可能为 null）
     * @param ejectedCase    抛出的弹壳（可生成掉落物，可能为 null）
     * @param malfunctionType 故障类型（成功时为 null）
     * @param failureReason  失败原因（成功时为 null）
     */
    public record CycleResult(
            boolean success,
            @Nullable GunStateData state,
            @Nullable FeedDeviceData updatedDevice,
            @Nullable LoadedRound ejectedCase,
            @Nullable MalfunctionType malfunctionType,
            @Nullable String failureReason
    ) {
        /** 成功 */
        public static CycleResult ok(GunStateData state, @Nullable FeedDeviceData updatedDevice,
                                     @Nullable LoadedRound ejectedCase) {
            return new CycleResult(true, state, updatedDevice, ejectedCase, null, null);
        }

        /** 故障 */
        public static CycleResult malfunction(GunStateData state, MalfunctionType type) {
            return new CycleResult(false, state.withMalfunction(type), null, null, type, null);
        }

        /** 失败（前置条件不满足） */
        public static CycleResult fail(String reason) {
            return new CycleResult(false, null, null, null, null, reason);
        }
    }
}
