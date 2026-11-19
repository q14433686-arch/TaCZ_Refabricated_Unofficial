package com.tacz.guns.test.mixin;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.test.MuzzleQuadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldEmitterManager.class, remap = false)
abstract class MuzzleSnapshotObserver {
    @Inject(method="snapshot", at=@At("RETURN"))
    private static void freeze(ParticleInstance p, CallbackInfoReturnable<ParticleInstance> ci) {
        MuzzleQuadProbe.snapshot(p, ci.getReturnValue());
    }
}
