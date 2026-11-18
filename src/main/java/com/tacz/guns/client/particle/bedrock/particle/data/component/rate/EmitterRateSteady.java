// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.rate;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IEmitterComponent;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleEmitterInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

/**
 * 持续发射。对应 "minecraft:emitter_rate_steady"。
 */
public record EmitterRateSteady(MolangExpression spawnRate, MolangExpression maxParticles)
        implements RateComponent {

    @Override public int order() { return 530; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(spawnRate, maxParticles);
    }

    public static EmitterRateSteady fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateSteady(
                molang.compile(getMolang(obj, "spawn_rate", "1")),
                molang.compile(getMolang(obj, "max_particles", "50")));
    }

    // ===== Runtime =====

    static final class Runtime implements IEmitterComponent {
        private final MolangExpression spawnRate;
        private final MolangExpression maxParticles;
        private float spawnAccumulator;

        Runtime(MolangExpression spawnRate, MolangExpression maxParticles) {
            this.spawnRate = spawnRate;
            this.maxParticles = maxParticles;
        }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            this.spawnAccumulator = 0;
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            if (!emitter.isActive()) return;
            MolangContext<?> ctx = emitter.getMolang().getContext();
            int maxP = (int) maxParticles.evaluate(ctx);
            float rate = (float) spawnRate.evaluate(ctx);
            spawnAccumulator += rate * emitter.getDt();
            while (spawnAccumulator >= 1f
                    && emitter.getParticleCount() < maxP) {
                spawnAccumulator -= 1f;
                emitter.spawnParticle();
            }
        }
    }
}
