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
 * 一次性发射。对应 "minecraft:emitter_rate_instant"。
 */
public record EmitterRateInstant(MolangExpression amount) implements RateComponent {

    @Override public int order() { return 530; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(amount);
    }

    public static EmitterRateInstant fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateInstant(molang.compile(getMolang(obj, "num_particles", "10")));
    }

    // ===== Runtime =====

    static final class Runtime implements IEmitterComponent {
        private final MolangExpression amount;
        private boolean hasEmitted;

        Runtime(MolangExpression amount) { this.amount = amount; }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            this.hasEmitted = false;
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            if (hasEmitted || !emitter.isActive()) return;
            MolangContext<?> ctx = emitter.getMolang().getContext();
            int count = Math.clamp((int) amount.evaluate(ctx), 0, ParticleEmitterInstance.MAX_PARTICLES);
            for (int i = 0; i < count; i++) {
                emitter.spawnParticle();
            }
            hasEmitted = true;
        }
    }
}
