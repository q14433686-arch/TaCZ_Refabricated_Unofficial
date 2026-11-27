package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.fabricmc.fabric.impl.client.indigo.renderer.accessor.AccessRenderCommandQueue;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.LinkedHashMap;
import java.util.List;

/** 1.21.11 Indigo sends a nonempty FRAPI mesh here *instead of* submitItem. Its default
 * order(0) would enqueue those faces into the preview wrapper's private, undrawn storage.
 * Copy just the aimed mesh into material batches; never retain the emitter's cursor or tint the held mesh. */
final class IndigoRackPreviewCollector extends RackPreviewCollector implements AccessRenderCommandQueue {
    IndigoRackPreviewCollector(SubmitNodeCollector output) { super(output); }

    @Override public void fabric_submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                                            int[] tints, List<BakedQuad> quads, RenderType layer, ItemStackRenderState.FoilType foil,
                                            MeshView mesh, ItemRenderTypeGetter getter) {
        submitItem(pose, context, light, overlay, outline, tints, quads, layer, foil);
        var batches = new LinkedHashMap<RenderType, MutableMesh>();
        mesh.forEach(q -> {
            var type = getter == null ? null : getter.renderType(q.atlas(), q.renderLayer());
            var batch = batches.computeIfAbsent(material(type == null ? layer : type), k -> Renderer.get().mutableMesh());
            batch.emitter().copyFrom(q).emit();
        });
        batches.forEach((type, batch) -> output.submitCustomGeometry(pose, type, (entry, out) -> batch.forEach(q -> {
            int tint = q.tintIndex() >= 0 && q.tintIndex() < tints.length ? tints[q.tintIndex()] : -1;
            var face = q.faceNormal();
            for (int i = 0; i < 4; i++) {
                int min = q.lightmap(i);
                int lit = q.emissive() ? LightTexture.FULL_BRIGHT : LightTexture.pack(
                        Math.max(LightTexture.block(light), LightTexture.block(min)), Math.max(LightTexture.sky(light), LightTexture.sky(min)));
                boolean normal = q.hasNormal(i);
                out.addVertex(entry.pose(), q.x(i), q.y(i), q.z(i)).setColor(alpha(ARGB.multiply(q.color(i), tint)))
                        .setUv(q.u(i), q.v(i)).setOverlay(overlay).setLight(lit)
                        .setNormal(entry, normal ? q.normalX(i) : face.x(), normal ? q.normalY(i) : face.y(), normal ? q.normalZ(i) : face.z());
            }
        })));
    }
}
