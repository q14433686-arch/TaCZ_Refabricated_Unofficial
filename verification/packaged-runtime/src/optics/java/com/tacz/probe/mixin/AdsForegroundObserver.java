package com.tacz.probe.mixin;

import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.probe.AdsTransitionSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ScopeFinalOverlayState.class,remap=false)
abstract class AdsForegroundObserver {
    @Inject(method="renderAfterFinalComposite",at=@At("HEAD"),remap=false)
    private static void beforeInk(CallbackInfo ci) { AdsTransitionSmoke.afterLens(); }
    @Inject(method="renderAfterFinalComposite",at=@At("RETURN"),remap=false)
    private static void afterInk(CallbackInfo ci) { AdsTransitionSmoke.afterInk(); }
}
