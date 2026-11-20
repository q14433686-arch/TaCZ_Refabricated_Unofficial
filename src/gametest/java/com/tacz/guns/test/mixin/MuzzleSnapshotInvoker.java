package com.tacz.guns.test.mixin;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=WorldEmitterManager.class, remap=false)
public interface MuzzleSnapshotInvoker {
    @Invoker("snapshot") static ParticleInstance taczTest$freeze(ParticleInstance p) { throw new AssertionError(); }
}
