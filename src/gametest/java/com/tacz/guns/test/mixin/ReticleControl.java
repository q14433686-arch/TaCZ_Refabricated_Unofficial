package com.tacz.guns.test.mixin;

import com.tacz.guns.client.render.scope.IReticleRenderer;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.test.ShaderOpticSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Negative control: same shaded scene, body, aperture, PIP and camera; remove only the division draw. */
@Mixin(IReticleRenderer.Context.class)
abstract class ReticleControl {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void withoutDisplay(BedrockRenderSnapshot snapshot, boolean emissive, CallbackInfo ci) {
        if (ShaderOpticSmoke.withoutReticle) ci.cancel();
    }
}
