// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

/**
 * 粒子光照外观组件。对应 "minecraft:particle_appearance_lighting"。
 * <p>
 * 标记组件，无字段。存在时粒子将根据游戏内光照条件着色。
 */
public record ParticleAppearanceLighting()
        implements IParticleComponentDefinition, IParticleComponent {

    private static final ParticleAppearanceLighting INSTANCE = new ParticleAppearanceLighting();

    public static ParticleAppearanceLighting instance() {
        return INSTANCE;
    }
}
