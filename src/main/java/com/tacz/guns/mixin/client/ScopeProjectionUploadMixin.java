package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.tacz.guns.client.render.scope.ScopeProjection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectionMatrixBuffer.class)
abstract class ScopeProjectionUploadMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void tacz$rememberProjection(Matrix4f matrix, CallbackInfoReturnable<GpuBufferSlice> cir) {
        ScopeProjection.uploaded(cir.getReturnValue(), matrix);
    }
}
