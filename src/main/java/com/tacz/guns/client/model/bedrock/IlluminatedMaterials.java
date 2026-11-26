package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.mixin.client.RenderSetupTextureAccessor;
import com.tacz.guns.mixin.client.RenderTypeStateAccessor;
import com.tacz.guns.mixin.client.TextureBindingAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Unshaded versions of the three body materials TaCZ actually submits. The entity shader flips
 * the diffuse term on back faces. A constant normal can therefore make a whole glowing cube dark.
 * Disable cardinal lighting, NOT the lightmap: the pack's real-sky option must still reach UV2. */
public final class IlluminatedMaterials {
    private enum Body { CUTOUT, TRANSLUCENT, CLIPPED_CUTOUT, CLIPPED_TRANSLUCENT }
    private record Pass(Body body, boolean hand) {
        boolean clipped() { return body == Body.CLIPPED_CUTOUT || body == Body.CLIPPED_TRANSLUCENT; }
        boolean translucent() { return body == Body.TRANSLUCENT || body == Body.CLIPPED_TRANSLUCENT; }
    }
    private record Key(Pass pass, Identifier texture, boolean outline) {}
    private static final Map<Pass, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    /** Unknown/specialised materials are owned by their caller. Never strip a custom sampler,
     * reticle mask, texture transform, depth test or layering transform by guessing from its texture. */
    public static RenderType forBody(RenderType original, boolean hand) {
        // A self-lit bone still casts the same silhouette, with the same UV/alpha coverage.
        // Iris re-submits the external model in light space and has a separate program map
        // for shadows. Use the original, already shadow-mapped material for that pass only.
        if (!hand && IrisCompat.isRenderShadow()) return original;
        Identifier texture = texture(original);
        Body body = classify(original, texture);
        if (body == null) return original;
        return TYPES.computeIfAbsent(new Key(new Pass(body, hand), texture, original.outline().isPresent()), IlluminatedMaterials::create);
    }

    public static Identifier texture(RenderType type) {
        type = ScopeRenderTypes.unwrapDepthCopy(type);
        var setup = ((RenderTypeStateAccessor)(Object)type).tacz$renderSetup();
        var binding = ((RenderSetupTextureAccessor)(Object)setup).tacz$textures().get("Sampler0");
        return binding instanceof TextureBindingAccessor sampler ? sampler.tacz$location() : null;
    }
    private static Body classify(RenderType original, Identifier texture) {
        if (texture == null) return null;
        boolean outline = original.outline().isPresent();
        if (original.pipeline() == RenderPipelines.ENTITY_CUTOUT && original == RenderTypes.entityCutout(texture)) return Body.CUTOUT;
        if (original.pipeline() == RenderPipelines.ENTITY_TRANSLUCENT && original == RenderTypes.entityTranslucent(texture, outline)) return Body.TRANSLUCENT;
        if (!ScopeRenderTypes.isClippedBody(original, texture)) return null;
        return original.pipeline().getBlendFunction().isPresent() ? Body.CLIPPED_TRANSLUCENT : Body.CLIPPED_CUTOUT;
    }

    /** Recognize body albedo only. Optics, overlays and particles must not acquire reflected light. */
    public static Identifier bodyTexture(RenderType type) {
        var texture = texture(type);
        return classify(type, texture) == null ? null : texture;
    }

    /** The preview collector changes cutout to translucent. Keep the illuminated variant there too. */
    public static RenderType preview(RenderType original, Identifier texture) {
        RenderType translucent = RenderTypes.entityTranslucent(texture);
        return PIPELINES.containsValue(original.pipeline()) ? forBody(translucent, false) : translucent;
    }

    private static RenderType create(Key key) {
        var setup = RenderSetup.builder(PIPELINES.computeIfAbsent(key.pass, IlluminatedMaterials::pipeline))
                .withTexture("Sampler0", key.texture).useOverlay().useLightmap();
        if (key.pass.clipped()) {
            ScopeRenderTypes.addApertureBindings(setup, key.texture);
        } else {
            setup.affectsCrumbling();
            if (key.outline) setup.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
        }
        if (key.pass.translucent()) setup.sortOnUpload();
        var type = RenderType.create("tacz_illuminated_body", setup.createRenderSetup());
        return key.pass.clipped() ? ScopeRenderTypes.outsideAperture(type) : type;
    }

    private static RenderPipeline pipeline(Pass pass) {
        String name = "illuminated_" + pass.body.name().toLowerCase(java.util.Locale.ROOT)
                + (pass.hand ? "_hand" : "_world");
        var builder = ScopeRenderTypes.clonePipeline(RenderPipelines.ENTITY_SOLID,
                        Identifier.fromNamespaceAndPath("tacz", "pipeline/" + name))
                .withShaderDefine("ALPHA_CUTOUT", .1f).withShaderDefine("NO_CARDINAL_LIGHTING").withCull(false);
        if (pass.translucent()) builder.withBlend(BlendFunction.TRANSLUCENT);
        if (pass.clipped()) ScopeRenderTypes.addOutsideApertureShader(builder);
        var result = builder.build();
        IrisCompat.assignPipelineToIris(result,
                pass.hand ? (pass.translucent() ? "HAND_TRANSLUCENT" : "HAND") : "ENTITIES", name);

        return result;
    }

    public static void clearTextures() { TYPES.clear(); }
    private IlluminatedMaterials() {}
}
