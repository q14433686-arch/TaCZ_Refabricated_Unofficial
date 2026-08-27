package me.xjqsh.lrtactical.api.animation;

/**
 * 消耗品动画上下文。
 *
 * <p>这些 public 方法就是内容包 Lua 脚本的 API 表面，
 * <b>方法名与官方 0.4.3 逐字一致</b>（{@code getStackCount / isUsing / getUsingTick}）——
 * 改名会让第三方内容包的 {@code .lua} 静默失效（Lua 调不存在的方法只得到 nil）。
 *
 * <h2>为什么继承 {@link BaseAnimationStateContext} 而不是直接继承
 * {@code ItemAnimationStateContext}</h2>
 * 官方那边消耗品与近战是两条平行分支，各自重复声明了 {@code currentItem}。
 * 本仓已在 {@code BaseAnimationStateContext} 里把 {@code currentItem}、
 * {@code getStackCount()} 以及全套输入查询（{@code isInputUp} 等）收敛好，
 * 这里复用即可，只新增「是否正在使用 / 已使用多少 tick」两个量。
 * 对 Lua 而言可见方法名是超集，官方脚本原样可跑。
 *
 * <p>与 {@link ThrowableAnimationStateContext} 结构相同、语义不同：
 * 投掷物的 {@code using} 指「拔销蓄力中」，消耗品的指「正在喝/正在打针」。
 * 两者不合并是为了让内容包脚本能各自独立演进（官方也是分开的）。
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
