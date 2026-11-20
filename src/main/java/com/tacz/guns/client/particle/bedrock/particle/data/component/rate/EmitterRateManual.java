// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.rate;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IEmitterComponent;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

/**
 * 手动发射模式。对应 "minecraft:emitter_rate_manual"。不自动发射粒子，由外部 API 触发。
 */
public record EmitterRateManual(MolangExpression maxParticles)
        implements RateComponent, IEmitterComponent {

    @Override public int order() { return 530; }
    @Override public boolean requireUpdate() { return false; }

    public static EmitterRateManual fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateManual(molang.compile(getMolang(obj, "max_particles", "50")));
    }
}
