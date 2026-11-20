package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.mixin.client.ThermalRenderSetupAccessor;
import com.tacz.guns.mixin.client.ThermalRenderTypeAccessor;
import com.tacz.guns.mixin.client.ThermalTextureBindingAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/** The heat mask stores surface coverage, not the optical material's translucency.
 * Replay the same model/pose/UVs with texture cutout coverage and ordinary depth testing. */
final class ScopeThermalMaterial {
    private static RenderPipeline pipeline(boolean shifted) {
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.parse(shifted ? "tacz:pipeline/thermal_shift_match" : "tacz:pipeline/thermal_coverage"))
                .withVertexShader(Identifier.parse("tacz:core/thermal_coverage"))
                .withFragmentShader(Identifier.parse("tacz:core/thermal_coverage"))
                .withColorTargetState(ColorTargetState.DEFAULT).withCull(false);
        if (shifted) builder.withShaderDefine("MATCH_SHIFTED_FACE")
                .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false));
        return builder.build();
    }
    private static final RenderPipeline COVERAGE = pipeline(false), SHIFT_MATCH = pipeline(true);
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>(), SHIFTED_TYPES = new HashMap<>();

    private ScopeThermalMaterial() {}

    static ModelFeatureRenderer.Submit<?> coverage(ModelFeatureRenderer.Submit<?> model, boolean shifted) {
        // Replay every eligible body with the sensor material, including the opaque
        // slime core. Reusing its optical/Iris material makes the face nearest the
        // copied scene depth disappear while translucent/back faces remain hot.
        var setup = ((ThermalRenderTypeAccessor)(Object)model.renderType()).tacz$thermalSetup();
        var binding = ((ThermalRenderSetupAccessor)(Object)setup).tacz$thermalTextures().get("Sampler0");
        // Nonstandard model shaders without a base texture keep their existing replay.
        if (!(binding instanceof ThermalTextureBindingAccessor texture)) return model;
        var type = (shifted ? SHIFTED_TYPES : TYPES).computeIfAbsent(texture.tacz$thermalTexture(), id -> RenderType.create("tacz_thermal_coverage",
                RenderSetup.builder(shifted ? SHIFT_MATCH : COVERAGE).withTexture("Sampler0", id).createRenderSetup()));
        return copy(model, type);
    }

    private static <S> ModelFeatureRenderer.Submit<S> copy(ModelFeatureRenderer.Submit<S> model, RenderType type) {
        return new ModelFeatureRenderer.Submit<>(type, model.pose(), model.model(), model.state(),
                model.lightCoords(), model.overlayCoords(), model.tintedColor(), model.sprite(), model.sheetedDecalPose());
    }

    static void clear() { TYPES.clear(); SHIFTED_TYPES.clear(); }
}
