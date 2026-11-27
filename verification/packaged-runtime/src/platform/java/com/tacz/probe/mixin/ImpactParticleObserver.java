package com.tacz.probe.mixin;

import com.tacz.probe.PlatformImpacts;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ImpactParticleObserver {
    @Inject(method="createParticle",at=@At("HEAD"))
    private void observe(ParticleOptions option,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> ci){PlatformImpacts.particle(option);}
}
