package me.xjqsh.lrtactical.api.animation;

/**
 * 消耗品（药品 / 食物）动画上下文。
 *
 * <p>方法名是内容包 Lua 的 API 表面，与官方 0.4.3 一致：
 * {@code getCurrentItem / getStackCount / isUsing / getUsingTick}
 * （以及从 {@link BaseAnimationStateContext} 继承的 {@code getPrepareTime} 与全部输入查询）。
 * 本仓自带的 {@code assets/lrtactical/scripts/consumable_state_machine.lua}
 * 用到的正是 {@code getStackCount} 与 {@code isUsing}。</p>
 *
 * <h2>与姊妹仓的一处刻意不同</h2>
 * 姊妹仓 TaCZ_Renovated 26.2 的 {@code ConsumableAnimationStateContext} 直接
 * {@code extends ItemAnimationStateContext}，把 {@code currentItem/using/usingTick}
 * 又写了一遍。本仓不照抄，改为 {@code extends BaseAnimationStateContext} ——
 * 理由与 {@link ThrowableAnimationStateContext} 类注释里记的完全相同：
 * 上游把同一组字段在两个平行类里各写一遍，属于重复定义；
 * 继承 Base 后 Lua 侧可见的方法名一个不少，还白拿
 * {@code isOnGround}、{@code isCrouching}、{@code isInputUp/Down/Left/Right} 与
 * {@code getWalkDist} 这些通用查询。
 *
 * <p><b>判定依据是 Lua 可见面而不是类层次</b>：脚本只按方法名调用，
 * 换成继承不会改变任何脚本的行为。</p>
 */
@SuppressWarnings("unused")
public class ConsumableAnimationStateContext extends BaseAnimationStateContext {
    private int usingTick = 0;
    private boolean using = false;

    public int getUsingTick() {
        return usingTick;
    }

    public void setUsingTick(int usingTick) {
        this.usingTick = usingTick;
    }

    public boolean isUsing() {
        return using;
    }

    public void setUsing(boolean using) {
        this.using = using;
    }
}
