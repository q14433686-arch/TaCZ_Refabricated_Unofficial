package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopeProjection;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 抄一份 1.21.11 {@code CachedPerspectiveProjectionMatrixBuffer} 算出的投影矩阵。
 *
 * <p>替代 26.2 的 {@code ScopeProjectionUploadMixin}：26.2 把
 * {@code ProjectionMatrixBuffer#writeBuffer} 的返回值（{@code GpuBufferSlice}）当作投影身份，
 * 因为这个类在那里就叫 {@code ProjectionMatrixBuffer}、且所有投影（关卡/手部）都走同一个类。
 * 1.21.11 是<b>两个</b>类：{@code PerspectiveProjectionMatrixBuffer}（关卡，矩阵由外部传入）与
 * {@code CachedPerspectiveProjectionMatrixBuffer}（3D HUD/手部，矩阵自己在
 * {@code createProjectionMatrix} 里算）。测距仪预测框需要的是后者，而它的矩阵没有任何公开读取点。</p>
 *
 * <p>注入点是私有的 {@code createProjectionMatrix(int,int,float)}：它只在宽/高/FOV 变化时被调用，
 * 因此这里的开销是"每次窗口尺寸变化一次"，不是每帧。</p>
 */
@Mixin(CachedPerspectiveProjectionMatrixBuffer.class)
abstract class CachedPerspectiveProjectionMixin {
    // Unlike the world projection buffer this cached HUD buffer computes its matrix only when
    // dimensions/FOV change, but returns a slice every frame. Index BOTH: a buffer-instance
    // mirror alone cannot identify the currently bound hand projection in ScopeLensFrame.
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void tacz$rememberProjectionSlice(CallbackInfoReturnable<com.mojang.blaze3d.buffers.GpuBufferSlice> cir) {
        var matrix = ScopeProjection.lastOf(this);
        if (matrix != null) ScopeProjection.uploaded(cir.getReturnValue(), matrix);
    }

    @Inject(method = "createProjectionMatrix", at = @At("RETURN"))
    private void tacz$mirrorProjection(int width, int height, float fov, CallbackInfoReturnable<Matrix4f> cir) {
        Matrix4f matrix = cir.getReturnValue();
        if (matrix != null) {
            ScopeProjection.mirrored((Object) this, matrix);
        }
    }
}
