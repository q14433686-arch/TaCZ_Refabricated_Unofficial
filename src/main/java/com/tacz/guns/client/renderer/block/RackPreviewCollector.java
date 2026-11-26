package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.mixin.client.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.List;
import net.minecraft.world.item.ItemDisplayContext;

/** A collector scoped to ONE preview. Native quad and TaCZ model submissions capture their
 * material and opacity now; no (global) shader color survives into another item's deferred draw. */
public class RackPreviewCollector extends SubmitNodeStorage {
    private static final float OPACITY = 0.65f;
    private static final boolean INDIGO = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fabric-renderer-indigo");
    final SubmitNodeCollector output;

    static RackPreviewCollector wrap(SubmitNodeCollector output) {
        return INDIGO ? new IndigoRackPreviewCollector(output) : new RackPreviewCollector(output);
    }
    RackPreviewCollector(SubmitNodeCollector output) { this.output = output; }

    static int alpha(int color) {
        return (color & 0xffffff) | Math.round((color >>> 24) * OPACITY) << 24;
    }
    static RenderType material(RenderType original) {
        var setup = ((PreviewRenderTypeAccessor)(Object)original).tacz$previewSetup();
        var sampler = ((PreviewRenderSetupAccessor)(Object)setup).tacz$previewTextures().get("Sampler0");
        return sampler instanceof PreviewTextureBindingAccessor t ? RenderTypes.entityTranslucent(t.tacz$previewTexture()) : original;
    }
    @Override public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer draw) {
        output.submitCustomGeometry(pose, material(type), (entry, out) -> draw.render(entry, new PreviewVertices(out)));
    }
    @Override public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                                     int[] tints, List<BakedQuad> quads, RenderType layer, ItemStackRenderState.FoilType foil) {
        // 1.21.11 assigns a render type to the layer, not to each quad. The vanilla item
        // renderer ignores tint values on untinted faces, so apply alpha during vertex emission.
        if (quads.isEmpty()) return;
        output.submitCustomGeometry(pose, material(layer), (entry, out) -> {
            for (var quad : quads) {
                int i = quad.tintIndex();
                int color = alpha(i >= 0 && i < tints.length ? tints[i] : -1);
                out.putBulkData(entry, quad, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                        (color & 255) / 255f, (color >>> 24) / 255f, light, overlay);
            }
        });
    }
    @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
                                      int color, TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        output.submitModel(model, state, pose, material(type), light, overlay, alpha(color), sprite, outline, crumbling);
    }

    private record PreviewVertices(VertexConsumer out) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z) { out.addVertex(x,y,z); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { out.setColor(r,g,b,Math.round(a * OPACITY)); return this; }
        public VertexConsumer setColor(int color) { out.setColor(alpha(color)); return this; }
        public VertexConsumer setUv(float u,float v) { out.setUv(u,v); return this; }
        public VertexConsumer setUv1(int u,int v) { out.setUv1(u,v); return this; }
        public VertexConsumer setUv2(int u,int v) { out.setUv2(u,v); return this; }
        public VertexConsumer setNormal(float x,float y,float z) { out.setNormal(x,y,z); return this; }
        public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
    }
}
