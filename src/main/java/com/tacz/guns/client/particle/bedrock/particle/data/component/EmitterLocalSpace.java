// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonObject;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getBoolean;

/**
 * 发射器局部空间组件。对应 "minecraft:emitter_local_space"。
 */
public record EmitterLocalSpace(boolean position, boolean rotation, boolean velocity)
        implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 400;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        emitter.setLocalSpaceFlags(position, position && rotation, velocity);
    }

    public static EmitterLocalSpace fromJson(JsonObject obj) {
        return new EmitterLocalSpace(
                getBoolean(obj, "position", false),
                getBoolean(obj, "rotation", false),
                getBoolean(obj, "velocity", false));
    }
}
