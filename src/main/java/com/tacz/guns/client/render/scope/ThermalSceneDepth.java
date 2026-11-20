package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** Borrow the original scene depth for an Iris sensor draw. The render pass attaches a
 * different texture (its private depth copy), so this is never a read/write feedback loop. */
final class ThermalSceneDepth extends AbstractTexture {
    static final Identifier ID = Identifier.parse("tacz:internal/thermal_scene_depth");
    static final String SAMPLER = "SceneDepthSampler";
    private static final ThermalSceneDepth INSTANCE = new ThermalSceneDepth();

    static ThermalSceneDepth borrow(RenderTarget scene) {
        INSTANCE.texture = scene.getDepthTexture();
        INSTANCE.textureView = scene.getDepthTextureView();
        INSTANCE.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        // register() is identity-safe, including across a TextureManager reload.
        Minecraft.getInstance().getTextureManager().register(ID, INSTANCE);
        return INSTANCE;
    }

    @Override public void close() {
        // Do not close either borrowed attachment or the cached sampler.
        texture = null; textureView = null; sampler = null;
    }
}
