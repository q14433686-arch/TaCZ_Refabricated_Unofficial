package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 1.21.11 {@code GameRenderer#hud3dProjectionMatrixBuffer}（3D HUD / 第一人称手部那一遍的投影）。
 *
 * <p>26.2 这里是一个 {@code Projection} 字段 {@code hudProjection}，直接 {@code getMatrix(clip)}
 * 就能读矩阵。1.21.11 的对应物是 {@code CachedPerspectiveProjectionMatrixBuffer}：只写 UBO、
 * 不暴露矩阵，所以矩阵要经 {@code CachedPerspectiveProjectionMixin} 抄到
 * {@link com.tacz.guns.client.render.scope.ScopeProjection}，这里只负责拿到<b>实例</b>做索引。</p>
 *
 * <p>26.2 的同名 accessor 还带 {@code fogRenderer}（给它那套掩码 pass 取"空雾"UBO 用）。
 * 本线的目镜遮罩走深度孔径路径、没有那个 pass，故不搬。</p>
 */
@Mixin(GameRenderer.class)
public interface GameRendererProjectionAccessor {
    @Accessor("hud3dProjectionMatrixBuffer")
    CachedPerspectiveProjectionMatrixBuffer tacz$getHudProjectionBuffer();
}
