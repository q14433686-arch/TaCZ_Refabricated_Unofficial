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
    private static final com.mojang.blaze3d.pipeline.RenderPipeline COPY = net.minecraft.client.renderer.RenderPipelines.register(
            com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                    .withLocation(Identifier.parse("tacz:pipeline/thermal_scene_depth"))
                    .withVertexShader(Identifier.parse("minecraft:core/screenquad"))
                    .withFragmentShader(Identifier.parse("tacz:core/thermal_scene_depth"))
                    .withVertexFormat(net.minecraft.client.renderer.RenderPipelines.ENTITY_OUTLINE_BLIT.getVertexFormat(),
                            net.minecraft.client.renderer.RenderPipelines.ENTITY_OUTLINE_BLIT.getVertexFormatMode())
                    .withSampler(SAMPLER).withCull(false).withoutBlend().withColorWrite(false)
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true).build());

    /** 1.21.11's private DEPTH32 attachment is fixed-point, not DEPTH32F. A bit-copy retains
     * sub-float rasterizer bits which a sampler cannot round-trip; EQUAL would reject the
     * shifted replay even when it writes the sampled scene depth. Canonicalize through the
     * SAME float sampler first. This retains scene occlusion and exact EQUAL comparison,
     * rather than introducing a polygon offset / permissive LEQUAL shifted-body workaround. */
    static void copyCanonical(RenderTarget scene, RenderTarget target) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "tacz_thermal_canonical_depth",
                target.getColorTextureView(), java.util.OptionalInt.empty(),
                target.getDepthTextureView(), java.util.OptionalDouble.of(1.0))) {
            pass.setPipeline(COPY);
            pass.bindTexture(SAMPLER, scene.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

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
