package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.client.model.bedrock.IlluminatedMaterials;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;

/** A collector scoped to ONE preview. Native quad and TaCZ model submissions capture their
 * material and opacity now; no (global) shader color survives into another item's deferred draw. */
public final class RackPreviewCollector extends SubmitNodeStorage {
    private final SubmitNodeCollector output;
    RackPreviewCollector(SubmitNodeCollector output) { this.output = output; }

    private static int alpha(int color) {
        return (color & 0xffffff) | Math.round((color >>> 24) * .65f) << 24;
    }
    private static RenderType material(RenderType original) {
        var texture = IlluminatedMaterials.texture(original);
        return texture == null ? original : IlluminatedMaterials.preview(original, texture);
    }
    @Override public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer draw) {
        output.submitCustomGeometry(pose, material(type), (entry, out) -> draw.render(entry, new PreviewVertices(out)));
    }
    @Override public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                                     int[] tints, List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) {
        for (var quad : quads) {
            int i = quad.tintIndex();
            int color = alpha(i >= 0 && i < tints.length ? tints[i] : -1);
            output.submitCustomGeometry(pose, material(type), (entry, out) -> {
                out.putBulkData(entry, quad, ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color),
                        ARGB.alphaFloat(color), light, overlay);
            });
        }
    }
    @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
                                      int color, TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        output.submitModel(model, state, pose, material(type), light, overlay, alpha(color), sprite, outline, crumbling);
    }

    private record PreviewVertices(VertexConsumer out) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z) { out.addVertex(x,y,z); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { out.setColor(r,g,b,Math.round(a*.65f)); return this; }
        public VertexConsumer setColor(int color) { out.setColor(alpha(color)); return this; }
        public VertexConsumer setUv(float u,float v) { out.setUv(u,v); return this; }
        public VertexConsumer setUv1(int u,int v) { out.setUv1(u,v); return this; }
        public VertexConsumer setUv2(int u,int v) { out.setUv2(u,v); return this; }
        public VertexConsumer setNormal(float x,float y,float z) { out.setNormal(x,y,z); return this; }
        public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
    }
}
