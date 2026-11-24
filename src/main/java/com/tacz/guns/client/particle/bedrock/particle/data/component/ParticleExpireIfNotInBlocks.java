// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 粒子方块过期组件。对应 "minecraft:particle_expire_if_not_in_blocks"。
 * <p>
 * 当粒子不在指定方块内时过期。
 *
 * @param blocks 方块 ID 集合（如 "minecraft:water"）
 */
public record ParticleExpireIfNotInBlocks(Set<String> blocks)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 340;
    }

    @Override
    public boolean requireUpdate() {
        return !blocks.isEmpty();
    }

    public static ParticleExpireIfNotInBlocks fromJson(JsonElement value) {
        Set<String> blocks = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            for (JsonElement elem : arr) {
                blocks.add(elem.getAsString());
            }
        }
        return new ParticleExpireIfNotInBlocks(blocks);
    }
}
