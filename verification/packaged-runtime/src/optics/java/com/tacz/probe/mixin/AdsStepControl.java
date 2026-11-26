package com.tacz.probe.mixin;

import com.tacz.guns.client.gameplay.LocalPlayerAim;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.probe.AdsTransitionSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Software-renderer fixture fixes duration in ticks, not aim state, pose or draw output. */
@Mixin(value=LocalPlayerAim.class,remap=false)
abstract class AdsStepControl {
    @Inject(method="getAlphaProgress",at=@At("HEAD"),cancellable=true,remap=false)
    private void duration(GunData gun,CallbackInfoReturnable<Float> cir) {
        if(AdsTransitionSmoke.running) cir.setReturnValue(.05f);
    }
}
