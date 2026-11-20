package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.client.render.scope.ScopeMaskTextureHandle;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.mixin.client.ThermalRenderSetupAccessor;
import com.tacz.guns.mixin.client.ThermalRenderTypeAccessor;
import com.tacz.guns.mixin.client.ThermalTextureBindingAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
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
    private enum Body { CUTOUT, TRANSLUCENT, CLIPPED }
    private record Pass(Body body, boolean hand) {}
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

    private static Identifier texture(RenderType type) {
        var setup = ((ThermalRenderTypeAccessor)(Object)type).tacz$thermalSetup();
        var binding = ((ThermalRenderSetupAccessor)(Object)setup).tacz$thermalTextures().get("Sampler0");
        return binding instanceof ThermalTextureBindingAccessor sampler ? sampler.tacz$thermalTexture() : null;
    }
    private static Body classify(RenderType original, Identifier texture) {
        if (texture == null) return null;
        boolean outline = original.outline().isPresent();
        if (original.pipeline() == RenderPipelines.ENTITY_CUTOUT && original == RenderTypes.entityCutout(texture, outline)) return Body.CUTOUT;
        if (original.pipeline() == RenderPipelines.ENTITY_TRANSLUCENT && original == RenderTypes.entityTranslucent(texture, outline)) return Body.TRANSLUCENT;
        return ScopeBodyRenderTypes.isClippedBody(original, texture) ? Body.CLIPPED : null;
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
        if (key.pass.body == Body.CLIPPED) {
            setup.withTexture(ScopeBodyRenderTypes.maskSamplerName(), ScopeMaskTextureHandle.ID);
        } else {
            setup.affectsCrumbling();
            if (key.outline) setup.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if (key.pass.body == Body.TRANSLUCENT) setup.sortOnUpload(); // 26.2 sorted-alpha path; no OIT phases exist in this version.
        }
        return RenderType.create("tacz_illuminated_body", setup.createRenderSetup());
    }

    private static RenderPipeline pipeline(Pass pass) {
        boolean clipped = pass.body == Body.CLIPPED;
        String name = (clipped ? "scope_body_illuminated" : "illuminated_" + (pass.body == Body.TRANSLUCENT ? "translucent" : "cutout"))
                + (pass.hand ? "_hand" : "_world");
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/" + name))
                .withShaderDefine("ALPHA_CUTOUT", .1f).withShaderDefine("NO_CARDINAL_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1).withCull(false)
                .withColorTargetState(pass.body == Body.TRANSLUCENT ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT);
        if (clipped) {
            var shader = Identifier.fromNamespaceAndPath("tacz", "core/scope_body");
            builder.withVertexShader(shader).withFragmentShader(shader).withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(ScopeBodyRenderTypes.maskSamplerLayout());
        }
        var result = builder.build();
        IrisCompat.assignEntityPipeline(result, pass.hand, true);

        return result;
    }

    public static void clearTextures() { TYPES.clear(); }
    private IlluminatedMaterials() {}
}
