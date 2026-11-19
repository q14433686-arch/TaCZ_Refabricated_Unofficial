package com.tacz.guns.client.model.bedrock;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.client.render.scope.ScopeMaskTextureHandle;
import com.tacz.guns.client.render.scope.ScopePipelinePrewarm;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.mixin.client.ThermalRenderSetupAccessor;
import com.tacz.guns.mixin.client.ThermalRenderTypeAccessor;
import com.tacz.guns.mixin.client.ThermalTextureBindingAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
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
        var setup = ((ThermalRenderTypeAccessor)(Object)original).tacz$thermalSetup();
        var binding = ((ThermalRenderSetupAccessor)(Object)setup).tacz$thermalTextures().get("Sampler0");
        if (!(binding instanceof ThermalTextureBindingAccessor sampler)) return original;
        Identifier texture = sampler.tacz$thermalTexture();
        boolean outline = original.outline().isPresent();
        Body body;
        if (original.pipeline() == RenderPipelines.ENTITY_CUTOUT && original == RenderTypes.entityCutout(texture, outline)) body = Body.CUTOUT;
        else if (original.pipeline() == RenderPipelines.ENTITY_TRANSLUCENT && original == RenderTypes.entityTranslucent(texture, outline)) body = Body.TRANSLUCENT;
        else if (ScopeBodyRenderTypes.isClippedBody(original, texture)) body = Body.CLIPPED;
        else return original;
        return TYPES.computeIfAbsent(new Key(new Pass(body, hand), texture, outline), IlluminatedMaterials::create);
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
            if (key.pass.body == Body.TRANSLUCENT) setup.sortOnUpload().setOitPipelines(Translucent.OIT);
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
        ScopePipelinePrewarm.touch(result);
        return result;
    }

    private static final class Translucent {
        // Alpha and depth stages do not shade colours. Only replace the accumulation stage.
        private static final OitPipelineSet OIT = new OitPipelineSet(RenderPipelines.OIT_ENTITY.depthBoundsPipeline(),
                RenderPipelines.OIT_ENTITY.transmittancePipeline(),
                RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.OIT_ACCUMULATE_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/oit_illuminated_body"))
                        .withCull(false).withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER1).withBindGroupLayout(BindGroupLayouts.SAMPLER2).build());
    }

    public static void clearTextures() { TYPES.clear(); }
    private IlluminatedMaterials() {}
}
