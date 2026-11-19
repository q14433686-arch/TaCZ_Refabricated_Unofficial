package com.tacz.guns.test.mixin;

import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.test.ReimaginedSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GunItemRendererWrapper.class,remap=false)
abstract class ReimaginedExposure {
    @Inject(method="renderFirstPerson",at=@At("HEAD"))
    private void holdExposure(CallbackInfo ci) { ReimaginedSmoke.refreshPulse(); }
}
