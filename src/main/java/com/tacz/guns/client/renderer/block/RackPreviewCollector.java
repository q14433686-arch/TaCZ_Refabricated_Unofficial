package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.mixin.client.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
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
        var setup = ((ThermalRenderTypeAccessor)(Object)original).tacz$thermalSetup();
        var sampler = ((ThermalRenderSetupAccessor)(Object)setup).tacz$thermalTextures().get("Sampler0");
        return sampler instanceof ThermalTextureBindingAccessor t ? com.tacz.guns.client.model.bedrock.IlluminatedMaterials.preview(original, t.tacz$thermalTexture()) : original;
    }
    @Override public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer draw) {
        output.submitCustomGeometry(pose, material(type), (entry, out) -> draw.render(entry, new PreviewVertices(out)));
    }
    @Override public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                                     int[] tints, ItemQuads quads, ItemStackRenderState.FoilType foil) {
        for (var quad : quads.all()) {
            var info = quad.materialInfo();
            int i = info.tintIndex();
            int color = alpha(i >= 0 && i < tints.length ? tints[i] : -1);
            output.submitCustomGeometry(pose, material(info.itemRenderType()), (entry, out) -> {
                var instance = new QuadInstance();
                instance.setColor(color);
                instance.setLightCoords(light);
                instance.setOverlayCoords(overlay);
                out.putBakedQuad(entry, quad, instance);
            });
        }
    }
    @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
                                      int color, UvMapping mapping, int outline) {
        output.submitModel(model, state, pose, material(type), light, overlay, alpha(color), mapping, outline);
    }

    private record PreviewVertices(VertexConsumer out) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z) { out.addVertex(x,y,z); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { out.setColor(r,g,b,Math.round(a*.65f)); return this; }
        public VertexConsumer setColor(int color) { out.setColor(alpha(color)); return this; }
        public VertexConsumer setUv(float u,float v) { out.setUv(u,v); return this; }
        public VertexConsumer setUv1(int u,int v) { out.setUv1(u,v); return this; }
        public VertexConsumer setUv3(float u,float v) { out.setUv3(u,v); return this; }
        public VertexConsumer setUv2(int u,int v) { out.setUv2(u,v); return this; }
        public VertexConsumer setNormal(float x,float y,float z) { out.setNormal(x,y,z); return this; }
        public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
    }
}
