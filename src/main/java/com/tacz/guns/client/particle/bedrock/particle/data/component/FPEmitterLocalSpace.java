// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonObject;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getBoolean;

public record FPEmitterLocalSpace(boolean position, boolean rotation, boolean velocity, boolean toWorld)
        implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 410;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        emitter.enableFPMode(position, position && rotation, velocity, toWorld);
    }

    public static FPEmitterLocalSpace fromJson(JsonObject obj) {
        return new FPEmitterLocalSpace(
                getBoolean(obj, "position", false),
                getBoolean(obj, "rotation", false),
                getBoolean(obj, "velocity", false),
                getBoolean(obj, "to_world", false));
    }
}
