package cn.sh1rocu.tacz.api.extension;

/**
 * 为实体补回 26.2 中被移除的 {@code walkDistO}（上一 tick 的行走距离）。
 *
 * <p><b>为什么需要它</b></p>
 *
 * <p>上游 1.21.1 的持枪行走动画驱动量是<b>插值后</b>的行走距离：</p>
 * <pre>
 * entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks
 * </pre>
 *
 * <p>26.2 把 {@code walkDist} 更名为 {@code moveDist}，但<b>没有</b>保留配套的
 * {@code walkDistO}（javap 确认：{@code Entity} 只有 {@code public float moveDist}）。
 * 第 6 轮为修正"动画快 6.7 倍"改用了 {@code moveDist}，量纲正确了，
 * 但它每游戏刻（20Hz）才更新一次、又无法插值 —— 渲染是按帧跑的（60~144Hz），
 * 于是动画呈现明显的阶梯感，看起来像<b>掉帧 / 被抽帧</b>。</p>
 *
 * <p>本接口由 {@code EntityMixin} 实现：在每个 {@code Entity#tick()} 的 HEAD
 * 把上一 tick 的 {@code moveDist} 存下来，从而重建出与上游等价的
 * {@code walkDistO}，让 {@code GunAnimationStateContext#getWalkDist()}
 * 能做与 1.21.1 完全一致的线性插值。</p>
 */
public interface IMoveDistTracker {
    /**
     * @return 上一游戏刻结束时的 {@code moveDist}。首次调用时与当前值相同（增量为 0）。
     */
    float tacz$getMoveDistO();
}
