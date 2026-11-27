package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the vertex format a {@link RenderPipeline} was <b>declared</b> with (1.21.11 field
 * {@code vertexFormat}), bypassing {@code RenderPipeline#getVertexFormat()}.
 *
 * <p>Iris (1.21.11 branch, {@code MixinRenderPipeline#iris$change}) injects at RETURN of
 * {@code getVertexFormat()} and, while a shader pack is active and the level is rendering,
 * replaces {@code NEW_ENTITY}/{@code BLOCK}/{@code POSITION_COLOR_TEX_LIGHTMAP} with
 * {@code IrisVertexFormats.ENTITY/TERRAIN/GLYPH}. A pipeline <i>cloned</i> from that return
 * value is permanently baked with the extended format. Once the pack is switched off, Iris no
 * longer fills {@code mc_midTexCoord}/{@code at_tangent}, and the first vertex written through
 * the cached clone throws {@code IllegalStateException: Missing elements in vertex}
 * (user crash report, 2026-11-26). Copying the declared format keeps Iris' redirect dynamic,
 * exactly like for vanilla pipelines.</p>
 */
@Mixin(RenderPipeline.class)
public interface RenderPipelineVertexFormatAccessor {
    @Accessor("vertexFormat")
    VertexFormat tacz$declaredVertexFormat();
}
