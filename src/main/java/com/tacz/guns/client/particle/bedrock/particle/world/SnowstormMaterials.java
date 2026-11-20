package com.tacz.guns.client.particle.bedrock.particle.world;

import com.mojang.blaze3d.pipeline.*;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleDescription;
import com.tacz.guns.client.render.FlashMaterials;
import com.tacz.guns.client.render.scope.*;
import com.tacz.guns.compat.iris.IrisCompat;
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
            case PARTICLES_OPAQUE -> clipped ? ScopeRenderTypes.clipForViewmodel(RenderTypes.entityCutout(texture), texture, true) : RenderTypes.entityCutout(texture);
            case PARTICLES_ALPHA, PARTICLES_BLEND -> clipped ? ScopeRenderTypes.flashTranslucentClipped(texture) : RenderTypes.entityTranslucent(texture);
            case ENERGY_SWIRL -> FlashMaterials.swirl(texture, clipped, 1);
            case PARTICLES_ADD, PARTICLES_EMISSIVE -> TYPES.computeIfAbsent(
                    new Key(new Pass(desc.getMaterial() == ParticleDescription.Material.PARTICLES_EMISSIVE, clipped, hand || clipped), texture), key -> {
                var setup = RenderSetup.builder(PIPELINES.computeIfAbsent(key.pass, SnowstormMaterials::pipeline))
                        .withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload();
                if (clipped) ScopeRenderTypes.addApertureBindings(setup, texture);
                var type = RenderType.create("tacz_snowstorm", setup.createRenderSetup());
                return clipped ? ScopeRenderTypes.outsideAperture(type) : type;
            });
        };
    }

    private static RenderPipeline pipeline(Pass pass) {
        String name = "snowstorm_" + (pass.emissive ? "emissive" : "add") + (pass.clipped ? "_clipped" : pass.hand ? "_hand" : "_world");
        var builder = ScopeRenderTypes.clonePipeline(RenderPipelines.ENTITY_SOLID, Identifier.parse("tacz:pipeline/" + name))
                .withCull(false)
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withBlend(BlendFunction.ADDITIVE);
        if (pass.emissive) builder.withShaderDefine("EMISSIVE").withShaderDefine("NO_CARDINAL_LIGHTING")
                .withDepthWrite(true);
        else builder.withShaderDefine("PER_FACE_LIGHTING").withDepthWrite(false);
        if (pass.clipped) ScopeRenderTypes.addOutsideApertureShader(builder);
        var pipeline = builder.build();
        IrisCompat.assignPipelineToIris(pipeline, pass.hand ? "HAND_TRANSLUCENT" : pass.emissive ? "EMISSIVE_ENTITIES" : "ENTITIES", name);

        return pipeline;
    }
    public static void clearTextures() { TYPES.clear(); }
    private SnowstormMaterials() {}
}
