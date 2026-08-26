package me.xjqsh.lrtactical.api.animation;

/**
 * 消耗品动画上下文。方法名是内容包 Lua 的 API 表面，与官方 0.4.3 保持一致。
 *
 * <p>与 {@link ThrowableAnimationStateContext} 同样处理：官方 0.4.3 把本类写成直接继承
 * {@code ItemAnimationStateContext}、并平行地重复定义 {@code currentItem/getStackCount}
 * （与 {@link BaseAnimationStateContext} 里的字段撞名）。本仓库已在
 * {@link ThrowableAnimationStateContext} 上纠正过这个「平行继承、重复定义」的问题 ——
 * 这里改为 {@code extends BaseAnimationStateContext}，复用
 * {@code currentItem/getStackCount} 与全部输入查询方法，只新增使用状态。
 *
 * <p><b>Lua 侧可见的方法名与官方 0.4.3 完全一致</b>
 * （{@code getCurrentItem/getStackCount/isUsing/getUsingTick}），因此消耗品内容包脚本无需改动。
 */
@SuppressWarnings("unused")
public class ConsumableAnimationStateContext extends BaseAnimationStateContext {
    private boolean using = false;
    private int usingTick = 0;

    public boolean isUsing() {
        return using;
    }

    public void setUsing(boolean using) {
        this.using = using;
    }

    public int getUsingTick() {
        return usingTick;
    }

    public void setUsingTick(int usingTick) {
        this.usingTick = usingTick;
    }
}
