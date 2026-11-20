// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

public record ParticleInitialSpin(MolangExpression rotation, MolangExpression rotationRate)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -490;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.rotation = (float) rotation.evaluate(ctx);
        p.rotationRate = (float) rotationRate.evaluate(ctx);
    }

    public static ParticleInitialSpin fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpin(
                molang.compile(getMolang(obj, "rotation", "0")),
                molang.compile(getMolang(obj, "rotation_rate", "0")));
    }
}
