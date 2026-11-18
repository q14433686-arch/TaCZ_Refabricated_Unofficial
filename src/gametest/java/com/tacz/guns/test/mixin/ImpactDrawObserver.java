package com.tacz.guns.test.mixin;

import com.tacz.guns.client.model.functional.ImpactMarkerRender;
import com.tacz.guns.test.ImpactMarkerSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disable only the marker vertex emission for a control screenshot, not the world, HUD or text. */
@Mixin(value = ImpactMarkerRender.class, remap = false)
abstract class ImpactDrawObserver {
    @Inject(method = "drawBox", at = @At("HEAD"), cancellable = true)
    private static void beforeDraw(CallbackInfo ci) {
        if (ImpactMarkerSmoke.atDraw()) ci.cancel();
    }
}
