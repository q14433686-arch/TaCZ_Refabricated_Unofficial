package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Captures the current Mojang render pipeline before Iris sets up a draw.
 *
 * <p>The source of the bridge is the same HEAD hook Iris itself uses in
 * {@code MixinGlCommandEncoder}. In Minecraft 26.2 the target method has the signature
 * {@code trySetup(GlRenderPass, Collection<String>)}; an earlier port listed an extra
 * {@code missingResources} argument and therefore silently failed to apply. That left the scope
 * mask mode at its default while the hand-pass fix below made the mask visible again on Iris +
 * NVIDIA.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisGlCommandEncoderMixin {
    @Inject(method = "trySetup", at = @At("HEAD"), require = 0)
    private void tacz$captureScopeRenderPass(@Coerce Object glRenderPass,
                                             Collection<String> missingResources,
                                             CallbackInfoReturnable<Boolean> cir) {
        IrisScopeMaskState.captureRenderPass(glRenderPass);
    }
}
