// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public record ParticleLifetimeKillPlane(float a, float b, float c, float d)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 320;
    }

    @Override
    public boolean requireUpdate() {
        return true;
    }

    @Override
    public void apply(ParticleInstance p) {
        p.insideKillPlane = (a * p.x + b * p.y + c * p.z + d) < 0;
    }

    @Override
    public void update(ParticleInstance p) {
        boolean nowInside = (a * p.x + b * p.y + c * p.z + d) < 0;
        if (nowInside != p.insideKillPlane) {
            p.alive = false;
        }
    }

    public static ParticleLifetimeKillPlane fromJson(JsonElement value) {
        if (!value.isJsonArray()) throw new JsonParseException("particle_lifetime_kill_plane must be a JSON array");
        JsonArray arr = value.getAsJsonArray();
        if (arr.size() != 4) throw new JsonParseException("particle_lifetime_kill_plane array must have 4 elements");
        return new ParticleLifetimeKillPlane(
                arr.get(0).getAsFloat(),
                arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat(),
                arr.get(3).getAsFloat()
        );
    }
}
