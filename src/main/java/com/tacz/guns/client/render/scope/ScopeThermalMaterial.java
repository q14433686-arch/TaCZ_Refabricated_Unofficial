package com.tacz.guns.client.render.scope;


import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.mixin.client.RenderSetupTextureAccessor;
import com.tacz.guns.mixin.client.RenderTypeStateAccessor;
import com.tacz.guns.mixin.client.TextureBindingAccessor;
import net.minecraft.client.renderer.RenderPipelines;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/** The heat mask stores surface coverage, not the optical material's translucency.
 * Replay the same model/pose/UVs with texture cutout coverage and ordinary depth testing. */
final class ScopeThermalMaterial {
    enum Depth {
        NATIVE("thermal_coverage", false, false), SCENE("thermal_scene_coverage", true, false), SHIFTED("thermal_shift_match", true, true);
        private final RenderPipeline pipeline;
        private final Map<Identifier, RenderType> materials = new HashMap<>();
        Depth(String name, boolean matchScene, boolean shifted) {
            var b = RenderPipeline.builder()
                    .withVertexFormat(ScopeRenderTypes.declaredVertexFormat(RenderPipelines.ENTITY_CUTOUT_NO_CULL), RenderPipelines.ENTITY_CUTOUT_NO_CULL.getVertexFormatMode())
                    .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(true)
                    .withLocation(Identifier.parse("tacz:pipeline/" + name))
                    .withVertexShader(Identifier.parse("tacz:core/thermal_coverage"))
                    .withFragmentShader(Identifier.parse("tacz:core/thermal_coverage"))
                    .withColorWrite(true).withoutBlend().withCull(false);
            if (matchScene) b.withShaderDefine("MATCH_SCENE_DEPTH").withSampler(ThermalSceneDepth.SAMPLER);
            if (shifted) b.withShaderDefine("MATCH_SHIFTED_FACE")
                    .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST).withDepthWrite(false);
            pipeline = RenderPipelines.register(b.build());
        }
        RenderType forTexture(Identifier texture) {
            return materials.computeIfAbsent(texture, id -> {
                var setup = RenderSetup.builder(pipeline).withTexture("Sampler0",id);
                if (this != NATIVE) setup.withTexture(ThermalSceneDepth.SAMPLER,ThermalSceneDepth.ID);
                return new RenderType("tacz_thermal_coverage",setup.createRenderSetup());
            });
        }
    }

    private ScopeThermalMaterial() {}

    static Identifier texture(RenderType material) {
        var setup = ((RenderTypeStateAccessor)(Object)material).tacz$renderSetup();
        var binding = ((RenderSetupTextureAccessor)(Object)setup).tacz$textures().get("Sampler0");
        return binding instanceof TextureBindingAccessor t ? t.tacz$location() : null;
    }

    static void clear() { for (Depth depth : Depth.values()) depth.materials.clear(); }
}
