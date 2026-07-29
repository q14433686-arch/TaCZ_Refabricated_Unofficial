package me.xjqsh.lrtactical.api.animation;

/**
 * 投掷物动画上下文。
 *
 * <p>比 {@link BaseAnimationStateContext} 多出「是否正在使用 / 已使用多少 tick」两个量 ——
 * 手雷的拔销、蓄力、投出三段动画全靠它们驱动。
 *
 * <h2>与上游的差异</h2>
 * 上游把 {@code currentItem/prepareTime/usingTick/using} 四个字段在
 * {@code BaseAnimationStateContext} 与 {@code ThrowableAnimationStateContext} 里
 * <b>各写了一遍</b>（前者继承自 {@code ItemAnimationStateContext}，后者也直接继承它，
 * 两者是<b>平行</b>关系而非父子）。结果是同名字段重复定义、且 Base 里的
 * {@code usingTick/using} 根本没有 getter，纯属死代码。
 *
 * <p>这里改为 {@code ThrowableAnimationStateContext extends BaseAnimationStateContext}，
 * 复用 {@code currentItem/prepareTime} 与全部输入查询方法，只新增使用状态。
 * <b>Lua 侧可见的方法名与上游完全一致</b>（{@code getStackCount/getUsingTick/isUsing/getPrepareTime}），
 * 因此内容包脚本无需改动。
 */
@SuppressWarnings("unused")
public class ThrowableAnimationStateContext extends BaseAnimationStateContext {
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
