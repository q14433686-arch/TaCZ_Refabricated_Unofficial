package com.tacz.guns.test.mixin;

import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.test.ReimaginedSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=NativeMuzzleEffects.class,remap=false)
public interface ReimaginedShotControl {
    @Invoker("onShot") static void taczTest$shot(GunFirePostEvent event) { throw new AssertionError(); }
}
