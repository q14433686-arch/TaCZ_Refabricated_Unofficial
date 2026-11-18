// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.shape.EmitterShape;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;

import java.util.Random;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.molangFromElement;

public record ParticleInitialSpeed(MolangExpression speed)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -500;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        float s = (float) speed.evaluate(ctx);
        if (s == 0) return;
        EmitterShape shape = p.emitter.getDefinition().emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyDirection(p, ctx, new Random(), s);
        }
    }

    public static ParticleInitialSpeed fromJson(JsonElement value, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpeed(molang.compile(molangFromElement(value, "0")));
    }
}
