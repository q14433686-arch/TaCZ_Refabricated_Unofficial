package com.tacz.guns.client.renderer.item;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** One world-lit cutout quad instead of an external rig. Only vanilla's actual dropped-item
 * extraction provides both GROUND and the ItemEntity owner (not a display entity or a preview). */
enum DroppedGunSprite implements SpecialModelRenderer<Identifier> {
    INSTANCE;

    static @Nullable Identifier select(ItemStack stack, ItemDisplayContext context, @Nullable ItemOwner owner) {
        if (context != ItemDisplayContext.GROUND || !(owner instanceof ItemEntity drop)
                || !(BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem()) instanceof GunItemRendererWrapper)) return null;
        int distance = RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.get();
        if (distance < 0) return null;
        if (distance > 0) {
            // Match the existing world-mesh detail gates: an 8x scope makes the same drop
            // look much closer. PIP reuses the extracted item; deciding at draw time is too late.
            double radius = (double) distance * ScopePipRenderState.currentDetailZoom();
            if (Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceToSqr(drop.position()) < radius * radius) return null;
        }
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getMissingLodDropTexture).orElse(null);
    }

    @Override
    public void submit(Identifier texture, ItemDisplayContext displayContext, PoseStack item, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
        // ItemTransform has already translated by (-.5,-.5,-.5). Keep the vanilla bob,
        // model center, scale and per-copy offset, but face the camera instead of spinning
        // edge-on. World entity submit poses in 26.3 are camera-relative, in world axes.
        var matrix = item.last().pose();
        var center = matrix.transformPosition(.5f, .5f, .5f, new Vector3f());
        var scale = matrix.getScale(new Vector3f());
        var card = new PoseStack();
        card.translate(center.x, center.y, center.z);
        card.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        float w = .375f * scale.x, h = .375f * scale.y;
        collector.submitCustomGeometry(card, RenderTypes.entityCutout(texture), (pose, vertices) -> {
            vertex(pose, vertices, -w, -h, 0, 1, light, overlay);
            vertex(pose, vertices,  w, -h, 1, 1, light, overlay);
            vertex(pose, vertices,  w,  h, 1, 0, light, overlay);
            vertex(pose, vertices, -w,  h, 0, 0, light, overlay);
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float u, float v,
                               int light, int overlay) {
        out.addVertex(pose.pose(), x, y, 0).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
    }

    @Override public void getExtents(Consumer<Vector3fc> output) { TaczDynamicItemModel.visitExtents(output); }
    @Override public @Nullable Identifier extractArgument(ItemStack stack) { return null; } // requires context + owner
}
