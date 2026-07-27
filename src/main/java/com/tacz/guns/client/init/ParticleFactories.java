package com.tacz.guns.client.init;

import com.tacz.guns.client.particle.BulletHoleParticle;
import com.tacz.guns.init.ModParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

@Environment(EnvType.CLIENT)
public class ParticleFactories {
    public static void registerParticles() {
        ParticleProviderRegistry.getInstance().register(ModParticles.BULLET_HOLE, new BulletHoleParticle.Provider());
    }
}
