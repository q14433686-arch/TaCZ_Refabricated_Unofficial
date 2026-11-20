package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopeProjection;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 抄一份 1.21.11 的<b>关卡</b>投影矩阵。
 *
 * <p>26.2 的 {@code CameraRenderState#projectionMatrix} 在 1.21.11 不存在；
 * {@code PerspectiveProjectionMatrixBuffer#getBuffer(Matrix4f)} 是关卡投影唯一的入口
 * （{@code GameRenderer#renderLevel} 调用的 {@code levelProjectionMatrixBuffer.getBuffer(...)}），
 * 而矩阵本身只是被写进 UBO、没有读取口。这里在入参处镜像一份，
 * 供 {@link ScopeProjection#levelProjectionM11()} 使用。</p>
 *
 * <p>手部/3D HUD 投影走的是 {@code CachedPerspectiveProjectionMatrixBuffer}，与本类无关，
 * 因此不存在"分不清是哪一遍投影"的问题。</p>
 */
@Mixin(PerspectiveProjectionMatrixBuffer.class)
abstract class PerspectiveProjectionMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void tacz$mirrorLevelProjection(Matrix4f matrix, CallbackInfoReturnable<?> cir) {
        if (matrix != null) {
            ScopeProjection.mirroredLevel((Object) this, matrix);
        }
    }
}
