package com.tacz.guns.client.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.compat.iris.IrisCompat;
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
        if (!IrisCompat.isUsingRenderPack()) return clipped ? ScopeRenderTypes.flashSwirlClipped(texture)
                : RenderTypes.energySwirl(texture, uvOffset, uvOffset);
        return type(Layer.SWIRL, texture, clipped, uvOffset);
    }

    public static RenderType legacyStrip(Identifier texture, boolean clipped) {
        if (!IrisCompat.isUsingRenderPack()) return clipped ? ScopeRenderTypes.flashTranslucentClipped(texture)
                : RenderTypes.entityTranslucent(texture);
        return type(Layer.STRIP, texture, clipped, 0);
    }

    /** Only the additional reflected light. An equal-depth replay cannot light a pixel where
     * the original hand was occluded or discarded by the lens. No depth, normal or material writes. */
    public static RenderType reflection(Identifier texture) { return type(Layer.REFLECTION, texture, false, 0); }

    /** A soft receiver decal in the world pass; keep terrain depth and destination alpha. */
    public static RenderType surface() { return surface(SURFACE_TEXTURE); }
    public static RenderType surface(Identifier albedo) { return type(Layer.SURFACE, albedo, false, 0); }
    private static final Identifier SURFACE_TEXTURE = Identifier.parse("tacz:textures/effect/surface_white.png");

    private static RenderType type(Layer layer, Identifier texture, boolean clipped, int uvOffset) {
        return TYPES.computeIfAbsent(new Key(new Pass(layer, clipped), texture, uvOffset), key -> {
            var setup = RenderSetup.builder(PIPELINES.computeIfAbsent(key.pass, FlashMaterials::pipeline))
                    // If the pack has no spidereyes/textured program, Iris synthesizes an
                    // ENTITY shader from the vertex format, not our NO_OVERLAY define. Its
                    // Sampler1/UV1 read must hit the neutral overlay, also on a hand swirl.
                    .withTexture("Sampler0", texture).useLightmap().useOverlay();
            if (layer != Layer.SURFACE) setup.sortOnUpload(); // additive terrain faces need no depth sort
            if (clipped) ScopeRenderTypes.addApertureBindings(setup, texture);
            if (layer == Layer.SWIRL) setup.setTextureTransform(new TextureTransform.OffsetTextureTransform(uvOffset, uvOffset));
            var renderType = RenderType.create("tacz_muzzle_" + layer.name().toLowerCase(java.util.Locale.ROOT), setup.createRenderSetup());
            return clipped ? ScopeRenderTypes.outsideAperture(renderType) : renderType;
        });
    }

    private static RenderPipeline pipeline(Pass pass) {
        var name = pass.layer.name().toLowerCase(java.util.Locale.ROOT) + (pass.clipped ? "_clipped" : "");
        var b = ScopeRenderTypes.clonePipeline(RenderPipelines.ENTITY_SOLID, Identifier.parse("tacz:pipeline/muzzle_" + name))
                .withCull(false)
                .withShaderDefine("ALPHA_CUTOUT", pass.layer == Layer.SURFACE ? .001f : .1f).withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withBlend(pass.layer == Layer.STRIP ? BlendFunction.TRANSLUCENT : BlendFunction.ADDITIVE);
        if (pass.layer != Layer.SURFACE) b.withShaderDefine("NO_OVERLAY");
        if (pass.layer == Layer.SURFACE) b.withDepthWrite(false)
                .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE));
        if (pass.layer == Layer.SWIRL) b.withShaderDefine("APPLY_TEXTURE_MATRIX");
        if (pass.layer == Layer.REFLECTION) b.withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST).withDepthWrite(false);
        if (pass.clipped) ScopeRenderTypes.addOutsideApertureShader(b);
        var pipeline = b.build();
        IrisCompat.assignPipelineToIris(pipeline, pass.clipped ? "HAND_TRANSLUCENT" : "EMISSIVE_ENTITIES", name);

        return pipeline;
    }
    public static void clearTextures() { TYPES.clear(); }
    private FlashMaterials() {}
}
