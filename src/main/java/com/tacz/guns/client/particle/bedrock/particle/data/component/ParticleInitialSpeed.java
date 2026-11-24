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

public record ParticleInitialSpeed(MolangExpression speed, MolangExpression[] vector)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -500;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        if(vector!=null){p.vx=(float)vector[0].evaluate(ctx);p.vy=(float)vector[1].evaluate(ctx);p.vz=(float)vector[2].evaluate(ctx);return;}
        float s = (float) speed.evaluate(ctx);
        if (s == 0) return;
        EmitterShape shape = p.emitter.getDefinition().emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyDirection(p, ctx, new Random(), s);
        }
    }

    public static ParticleInitialSpeed fromJson(JsonElement value, ParticleMolangEnvironment molang) {
        if(value.isJsonArray()){
            var array=value.getAsJsonArray();if(array.size()!=3)throw new com.google.gson.JsonParseException("Initial speed vector requires 3 values");
            return new ParticleInitialSpeed(null,new MolangExpression[]{molang.compile(molangFromElement(array.get(0),"0")),
                molang.compile(molangFromElement(array.get(1),"0")),molang.compile(molangFromElement(array.get(2),"0"))});
        }
        return new ParticleInitialSpeed(molang.compile(molangFromElement(value, "0")),null);
    }
}
