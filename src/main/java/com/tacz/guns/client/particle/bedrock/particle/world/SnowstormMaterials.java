package com.tacz.guns.client.particle.bedrock.particle.world;

import com.mojang.renderpearl.api.pipeline.*;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleDescription;
import com.tacz.guns.client.render.scope.*;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/** SBM's add is lit and color-write-only; emissive is unlit with depth writes. Both are additive,
 * but neither uses the energy-swirl texture transform. Preserve those distinctions under the lens. */
public final class SnowstormMaterials {
    private record Pass(boolean emissive, boolean clipped, boolean hand) {}
    private record Key(Pass pass, Identifier texture) {}
    private static final Map<Pass, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    public static RenderType type(ParticleDescription desc, boolean clipped, boolean hand) {
        Identifier texture = desc.getTexture();
        return switch (desc.getMaterial()) {
            case PARTICLES_OPAQUE -> clipped ? ScopeBodyRenderTypes.clipped(texture) : RenderTypes.entityCutout(texture);
            case PARTICLES_ALPHA, PARTICLES_BLEND -> clipped ? ScopeBodyRenderTypes.flashTranslucent(texture) : RenderTypes.entityTranslucent(texture);
            case ENERGY_SWIRL -> clipped ? ScopeBodyRenderTypes.swirlClipped(texture, 1) : RenderTypes.energySwirl(texture, 1, 1);
            case PARTICLES_ADD, PARTICLES_EMISSIVE -> TYPES.computeIfAbsent(
                    new Key(new Pass(desc.getMaterial() == ParticleDescription.Material.PARTICLES_EMISSIVE, clipped, hand || clipped), texture), key -> {
                var setup = RenderSetup.builder(PIPELINES.computeIfAbsent(key.pass, SnowstormMaterials::pipeline))
                        .withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload();
                if (clipped) setup.withTexture(ScopeBodyRenderTypes.maskSamplerName(), ScopeMaskTextureHandle.ID);
                return RenderType.create("tacz_snowstorm", setup.createRenderSetup());
            });
        };
    }

    private static RenderPipeline pipeline(Pass pass) {
        String name = "snowstorm_" + (pass.emissive ? "emissive" : "add") + (pass.clipped ? "_clipped" : pass.hand ? "_hand" : "_world");
        var shader = Identifier.parse(pass.clipped ? "tacz:core/scope_body" : "minecraft:core/entity");
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.parse("tacz:pipeline/" + name))
                .withVertexShader(shader).withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1).withCull(false)
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE));
        if (pass.emissive) builder.withShaderDefine("EMISSIVE").withShaderDefine("NO_CARDINAL_LIGHTING")
                .withDepthStencilState(DepthStencilState.DEFAULT);
        else builder.withShaderDefine("PER_FACE_LIGHTING").withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false));
        if (pass.clipped) builder.withShaderDefine("SCOPE_MASK").withBindGroupLayout(ScopeBodyRenderTypes.maskSamplerLayout());
        var pipeline = builder.build();
        IrisCompat.assignEntityPipeline(pipeline, pass.hand, pass.emissive);
        ScopePipelinePrewarm.touch(pipeline);
        return pipeline;
    }
    public static void clearTextures() { TYPES.clear(); }
    private SnowstormMaterials() {}
}
