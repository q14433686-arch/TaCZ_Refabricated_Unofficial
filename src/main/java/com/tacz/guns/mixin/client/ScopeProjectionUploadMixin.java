package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.tacz.guns.client.render.scope.ScopeProjection;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11 等价物：{@code ProjectionMatrixBuffer#writeBuffer} 在本世代不存在，
 * 投影上传走 {@link PerspectiveProjectionMatrixBuffer#getBuffer(Matrix4f)}
 * （内部 Std140 打包 + {@code CommandEncoder.writeToBuffer} 上传，返回 GpuBufferSlice）。
 * 在上传返回处记录 CPU 镜像，供瞄具掩码 pass 使用实际绑定的投影矩阵。
 */
@Mixin(PerspectiveProjectionMatrixBuffer.class)
abstract class ScopeProjectionUploadMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void tacz$rememberProjection(Matrix4f matrix, CallbackInfoReturnable<GpuBufferSlice> cir) {
        ScopeProjection.uploaded(cir.getReturnValue(), matrix);
        // 本类是关卡投影唯一的 getBuffer 入口（手部/3D HUD 走 CachedPerspectiveProjectionMatrixBuffer，
        // 与本类无关），故这里同时镜像"关卡投影"，供 levelProjectionM11() 的三个消费点使用。
        ScopeProjection.mirroredLevel((Object) this, matrix);
    }
}
