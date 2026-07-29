package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Captures the current RenderPipeline before Iris sets up the shader program. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisGlCommandEncoderMixin {
    @Inject(method = "trySetup", at = @At("HEAD"), require = 0)
    private void tacz$captureScopeRenderPass(@Coerce Object glRenderPass,
                                             Collection<String> missingResources,
                                             CallbackInfoReturnable<Boolean> cir) {
        IrisScopeMaskState.captureRenderPass(glRenderPass);
    }
}
