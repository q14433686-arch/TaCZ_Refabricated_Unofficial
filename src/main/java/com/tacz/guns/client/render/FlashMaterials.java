package com.tacz.guns.client.render;

import com.mojang.renderpearl.api.pipeline.*;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.client.render.scope.ScopeMaskTextureHandle;
import com.tacz.guns.client.render.scope.ScopePipelinePrewarm;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Self-lit surfaces, not a higher column in a shader pack's coloured block lightmap.
 * Own the Iris registrations: remapping vanilla ENERGY_SWIRL or ENTITY_TRANSLUCENT would
 * change unrelated entities. Keep the original strip UVs, alpha and aperture coverage. */
public final class FlashMaterials {
    private enum Layer { SWIRL, STRIP, REFLECTION, SURFACE }
    private record Pass(Layer layer, boolean clipped) {}
    private record Key(Pass pass, Identifier texture, int uvOffset) {}
    private static final Map<Pass, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    public static RenderType swirl(Identifier texture, boolean clipped, int uvOffset) {
        if (!IrisCompat.hasActiveIrisPack()) return clipped ? ScopeBodyRenderTypes.swirlClipped(texture, uvOffset)
                : RenderTypes.energySwirl(texture, uvOffset, uvOffset);
        return type(Layer.SWIRL, texture, clipped, uvOffset);
    }

    public static RenderType legacyStrip(Identifier texture, boolean clipped) {
        if (!IrisCompat.hasActiveIrisPack()) return clipped ? ScopeBodyRenderTypes.flashTranslucent(texture)
                : RenderTypes.entityTranslucent(texture);
        return type(Layer.STRIP, texture, clipped, 0);
    }

    /** Only the additional reflected light. An equal-depth replay cannot light a pixel where
     * the original hand was occluded or discarded by the lens. No depth, normal or material writes. */
    public static RenderType reflection(Identifier texture) { return type(Layer.REFLECTION, texture, false, 0); }

    /** A soft receiver decal in the world pass; keep terrain depth and destination alpha. */
    public static RenderType surface() { return type(Layer.SURFACE, SURFACE_TEXTURE, false, 0); }
    private static final Identifier SURFACE_TEXTURE = Identifier.parse("tacz:textures/effect/surface_spill.png");

    private static RenderType type(Layer layer, Identifier texture, boolean clipped, int uvOffset) {
        return TYPES.computeIfAbsent(new Key(new Pass(layer, clipped), texture, uvOffset), key -> {
            var setup = RenderSetup.builder(PIPELINES.computeIfAbsent(key.pass, FlashMaterials::pipeline))
                    .withTexture("Sampler0", texture).useLightmap().sortOnUpload();
            // Iris's synthesized ENTITY program samples UV1 even for emissive surfaces.
            // Bind the actual neutral overlay texel, never an unrelated or unbound sampler.
            if (layer == Layer.SURFACE) setup.useOverlay();
            if (clipped) setup.withTexture(ScopeBodyRenderTypes.maskSamplerName(), ScopeMaskTextureHandle.ID);
            if (layer == Layer.SWIRL) setup.setTextureTransform(new TextureTransform.OffsetTextureTransform(uvOffset, uvOffset));
            return RenderType.create("tacz_muzzle_" + layer.name().toLowerCase(java.util.Locale.ROOT), setup.createRenderSetup());
        });
    }

    private static RenderPipeline pipeline(Pass pass) {
        var shader = Identifier.parse(pass.clipped ? "tacz:core/scope_body" : "minecraft:core/entity");
        var name = pass.layer.name().toLowerCase(java.util.Locale.ROOT) + (pass.clipped ? "_clipped" : "");
        var b = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.parse("tacz:pipeline/muzzle_" + name))
                .withVertexShader(shader).withFragmentShader(shader).withCull(false)
                .withShaderDefine("ALPHA_CUTOUT", pass.layer == Layer.SURFACE ? .001f : .1f).withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withColorTargetState(new ColorTargetState(pass.layer == Layer.STRIP ? BlendFunction.TRANSLUCENT : BlendFunction.ADDITIVE));
        if (pass.layer != Layer.SURFACE) b.withShaderDefine("NO_OVERLAY");
        if (pass.layer == Layer.SURFACE) b.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)));
        if (pass.layer == Layer.SWIRL) b.withShaderDefine("APPLY_TEXTURE_MATRIX");
        if (pass.layer == Layer.REFLECTION) b.withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false));
        if (pass.clipped) b.withShaderDefine("SCOPE_MASK").withBindGroupLayout(ScopeBodyRenderTypes.maskSamplerLayout());
        var pipeline = b.build();
        IrisCompat.assignEmissivePipeline(pipeline);
        ScopePipelinePrewarm.touch(pipeline);
        return pipeline;
    }
    public static void clearTextures() { TYPES.clear(); }
    private FlashMaterials() {}
}
