// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data;

import net.minecraft.resources.Identifier;

/**
 * 粒子效果描述符，对应 JSON 中的 "description" 块。
 * 包含 identifier、基础渲染参数（material）和纹理信息。
 */
public class ParticleDescription {
    private final Identifier identifier;
    private final Material material;
    private final Identifier texture;
    /** flipbook 纹理的列数，0 表示非 flipbook */
    private final int textureWidth;
    /** flipbook 纹理的行数 */
    private final int textureHeight;

    public ParticleDescription(Identifier identifier, Material material, Identifier texture,
                               int textureWidth, int textureHeight) {
        this.identifier = identifier;
        this.material = material;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public Material getMaterial() {
        return material;
    }

    public Identifier getTexture() {
        return texture;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public boolean isFlipbook() {
        return textureWidth > 0 && textureHeight > 0;
    }

    public enum Material {
        PARTICLES_BLEND,
        PARTICLES_OPAQUE,
        PARTICLES_ALPHA,
        PARTICLES_ADD,
        ENERGY_SWIRL,
        PARTICLES_EMISSIVE
    }
}
