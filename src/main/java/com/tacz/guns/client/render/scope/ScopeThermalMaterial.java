package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.tacz.guns.mixin.client.ThermalRenderSetupAccessor;
import com.tacz.guns.mixin.client.ThermalRenderTypeAccessor;
import com.tacz.guns.mixin.client.ThermalTextureBindingAccessor;
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
        NATIVE("thermal_coverage", false, false),
        SCENE("thermal_scene_coverage", true, false),
        SHIFTED("thermal_shift_match", true, true);

        private final RenderPipeline pipeline;
        private final Map<Identifier, RenderType> materials = new HashMap<>();

        Depth(String name, boolean matchScene, boolean shifted) {
            var b = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.parse("tacz:pipeline/" + name))
                    .withVertexShader(Identifier.parse("tacz:core/thermal_coverage"))
                    .withFragmentShader(Identifier.parse("tacz:core/thermal_coverage"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withCull(false);
            if (matchScene) {
                b.withShaderDefine("MATCH_SCENE_DEPTH").withSampler(ThermalSceneDepth.SAMPLER);
            }
            if (shifted) {
                b.withShaderDefine("MATCH_SHIFTED_FACE")
                        .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false));
            }
            pipeline = RenderPipelines.register(b.build());
        }

        RenderType forTexture(Identifier texture) {
            return materials.computeIfAbsent(texture, id -> {
                var setup = RenderSetup.builder(pipeline).withTexture("Sampler0", id).useLightmap();
                if (this != NATIVE) {
                    setup.withTexture(ThermalSceneDepth.SAMPLER, ThermalSceneDepth.ID);
                }
                return RenderType.create("tacz_thermal_coverage", setup.createRenderSetup());
            });
        }
    }

    private ScopeThermalMaterial() {}

    static RenderType coverageType(RenderType original, Depth depth) {
        var setup = ((ThermalRenderTypeAccessor) (Object) original).tacz$thermalSetup();
        var binding = ((ThermalRenderSetupAccessor) (Object) setup).tacz$thermalTextures().get("Sampler0");
        // Preserve nonstandard model materials without a base texture.
        if (!(binding instanceof ThermalTextureBindingAccessor texture)) {
            return original;
        }
        return depth.forTexture(texture.tacz$thermalTexture());
    }

    static void clear() {
        for (Depth depth : Depth.values()) {
            depth.materials.clear();
        }
    }
}
