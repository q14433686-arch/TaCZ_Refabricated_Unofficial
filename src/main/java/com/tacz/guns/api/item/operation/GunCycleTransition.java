package com.tacz.guns.api.item.operation;

import com.tacz.guns.api.item.enums.GunCycleState;
import com.tacz.guns.api.item.enums.MalfunctionType;
import com.tacz.guns.api.item.enums.ActionType;

import java.util.*;

/**
 * 枪机循环状态机转换表。
 * <p>
 * 定义所有合法的状态转换，非法转换被拒绝。
 * <p>
 * 状态机的设计原则：
 * <ul>
 *   <li>每个转换都由一个供弹原语驱动</li>
 *   <li>原语失败 → 进入 MALFUNCTION 状态</li>
 *   <li>MALFUNCTION 状态只能通过清除故障退出</li>
 *   <li>手动操作（拉栓/推栓）走 BOLT_OPEN/BOLT_CLOSE 分支</li>
 * </ul>
 * <p>
 * 对应设计文档：E.2.1 完整枪机循环状态机
 */
public final class GunCycleTransition {

    private GunCycleTransition() {}

    /**
     * 合法转换表。
     * <p>
     * Key: 当前状态 → Value: 可转换到的目标状态集合。
     * <p>
     * 转换逻辑：
     * <pre>
     * READY → FIRING          (扣扳机)
     * FIRING → UNLOCKING      (膛压建立，枪机开锁)
     * UNLOCKING → EXTRACTING  (枪机后拉，抽壳)
     * EXTRACTING → EJECTING   (弹壳被抛出)
     * EJECTING → FEEDING      (新弹推送入膛)
     * FEEDING → LOCKING       (枪机闭锁)
     * LOCKING → READY         (闭锁完成，可再次击发)
     * LOCKING → EMPTY         (弹匣空，无弹可入膛)
     * EMPTY → BOLT_OPEN       (手动拉栓/空仓挂机)
     * BOLT_OPEN → FEEDING     (手动推栓，从弹匣取弹)
     * BOLT_OPEN → EMPTY       (弹匣空，推栓后仍为空)
     * READY → BOLT_OPEN       (手动拉栓排弹)
     * 任何 → MALFUNCTION       (原语失败)
     * MALFUNCTION → EMPTY     (清除故障后)
     * </pre>
     */
    private static final Map<GunCycleState, Set<GunCycleState>> TRANSITIONS = new EnumMap<>(GunCycleState.class);

    static {
        // 正常射击循环
        addTransition(GunCycleState.READY, GunCycleState.FIRING);
        addTransition(GunCycleState.FIRING, GunCycleState.UNLOCKING);
        addTransition(GunCycleState.UNLOCKING, GunCycleState.EXTRACTING);
        addTransition(GunCycleState.EXTRACTING, GunCycleState.EJECTING);
        addTransition(GunCycleState.EJECTING, GunCycleState.FEEDING);
        addTransition(GunCycleState.FEEDING, GunCycleState.LOCKING);
        addTransition(GunCycleState.LOCKING, GunCycleState.READY);
        addTransition(GunCycleState.LOCKING, GunCycleState.EMPTY); // 弹匣空

        // 空仓/手动操作
        addTransition(GunCycleState.EMPTY, GunCycleState.BOLT_OPEN);
        addTransition(GunCycleState.BOLT_OPEN, GunCycleState.FEEDING);
        addTransition(GunCycleState.BOLT_OPEN, GunCycleState.EMPTY);
        addTransition(GunCycleState.READY, GunCycleState.BOLT_OPEN); // 手动拉栓

        // 故障
        // 任何循环状态都可以进入 MALFUNCTION
        for (GunCycleState state : GunCycleState.values()) {
            if (state != GunCycleState.MALFUNCTION && state != GunCycleState.EMPTY) {
                addTransition(state, GunCycleState.MALFUNCTION);
            }
        }
        // 故障清除后回到空膛
        addTransition(GunCycleState.MALFUNCTION, GunCycleState.EMPTY);

        // 手动操作
        addTransition(GunCycleState.BOLT_OPEN, GunCycleState.BOLT_CLOSE);
        addTransition(GunCycleState.BOLT_CLOSE, GunCycleState.READY);
        addTransition(GunCycleState.BOLT_CLOSE, GunCycleState.EMPTY);
    }

    private static void addTransition(GunCycleState from, GunCycleState to) {
        TRANSITIONS.computeIfAbsent(from, k -> EnumSet.noneOf(GunCycleState.class)).add(to);
    }

    /**
     * 检查从 from 到 to 的转换是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 是否合法
     */
    public static boolean isTransitionValid(GunCycleState from, GunCycleState to) {
        Set<GunCycleState> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 获取从指定状态可以转换到的所有目标状态。
     *
     * @param from 当前状态
     * @return 可转换到的目标状态集合
     */
    public static Set<GunCycleState> getValidTransitions(GunCycleState from) {
        return Collections.unmodifiableSet(TRANSITIONS.getOrDefault(from, EnumSet.noneOf(GunCycleState.class)));
    }

    /**
     * 验证转换是否合法，如果不合法则抛出异常。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 如果转换不合法
     */
    public static void validateTransition(GunCycleState from, GunCycleState to) {
        if (!isTransitionValid(from, to)) {
            throw new IllegalStateException(
                    String.format("Invalid GunCycleState transition: %s → %s. Valid transitions from %s: %s",
                            from, to, from, getValidTransitions(from)));
        }
    }

    /**
     * 获取自动原理类型对应的完整射击循环路径。
     * <p>
     * 自装填枪械：FIRING → UNLOCKING → EXTRACTING → EJECTING → FEEDING → LOCKING → READY
     * 手动枪械：FIRING → BOLT_OPEN → (手动推栓) → FEEDING → LOCKING → READY
     */
    public static List<GunCycleState> getFireCyclePath(ActionType actionType) {
        if (actionType == ActionType.MANUAL_ACTION) {
            return List.of(
                    GunCycleState.FIRING,
                    GunCycleState.BOLT_OPEN
                    // 等待手动推栓 → FEEDING → LOCKING → READY
            );
        }
        // 自装填枪械
        return List.of(
                GunCycleState.FIRING,
                GunCycleState.UNLOCKING,
                GunCycleState.EXTRACTING,
                GunCycleState.EJECTING,
                GunCycleState.FEEDING,
                GunCycleState.LOCKING,
                GunCycleState.READY
        );
    }
}
