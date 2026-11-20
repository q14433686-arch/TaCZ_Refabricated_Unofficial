package com.tacz.guns.test.mixin;

import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.test.ReimaginedSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=NativeMuzzleEffects.class,remap=false)
abstract class ReimaginedLocatorControl {
    @Inject(method="extract",at=@At("HEAD"),cancellable=true)
    private static void bodyWithoutBloom(CallbackInfoReturnable<Boolean> ci) { if(ReimaginedSmoke.bodyOnly)ci.setReturnValue(true); }
}
