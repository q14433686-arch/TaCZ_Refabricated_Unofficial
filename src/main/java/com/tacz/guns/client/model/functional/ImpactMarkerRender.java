package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.papi.RangefinderReadout;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopeProjection;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.mixin.client.GameRendererProjectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** The locator may be under a division hidden from the ordinary model submission. Resolve its
 * animated parent chain during extraction, not in the deferred vertex callback. */
public record ImpactMarkerRender(List<BedrockPart> path, Rangefinder settings) {
    public static final String NODE = "rangefinder_impact_prediction_pos";

    public static @Nullable ImpactMarkerRender of(@Nullable List<BedrockPart> path, Map<String, TextShow> nodes) {
        if (path == null) return null;
        // One locator per rig. Use a stable node selection even for an unordered text_show map.
        return nodes.entrySet().stream().filter(e -> e.getValue().getRangefinder() != null && e.getValue().getRangefinder().showsImpact())
                .min(Map.Entry.comparingByKey()).map(e -> new ImpactMarkerRender(List.copyOf(path), e.getValue().getRangefinder())).orElse(null);
    }

    public enum Layer { MODEL, APERTURE, FINAL_APERTURE }

    public void submit(ItemStack stack, PoseStack root, SubmitNodeCollector collector, Layer layer, int overlay) {
        double slope = RangefinderReadout.impactSlope(stack, settings);
        if (!Double.isFinite(slope)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // 1.21.11 没有 26.2 的 CameraRenderState#projectionMatrix；关卡投影经
        // ScopeProjection.mirroredLevel 镜像（见其注释），手部投影经 Cached 镜像 + accessor 反查。
        var camera = mc.gameRenderer.getLevelRenderState().cameraRenderState;
        if (!camera.initialized) return;
        double projected = -slope * ScopeProjection.levelProjectionM11();
        // 1.21.11 侧同一对量（见 ScopePipRenderState#currentWorldZoom 的说明）：
        // 26.2 的 suppressesWorldFovZoom()/currentWorldZoom() 在这里都要开镜进度。
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (ScopePipRenderState.suppressesWorldFovZoom(partialTicks)) {
            projected *= ScopePipRenderState.currentDetailZoom() / ScopePipRenderState.currentWorldZoom(partialTicks);
        }
        if (!Double.isFinite(projected) || Math.abs(projected) > 1) return;

        PoseStack pose = new PoseStack();
        pose.last().pose().set(root.last().pose());
        pose.last().normal().set(root.last().normal());
        for (var part : path) part.translateAndRotateAndScale(pose);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(2f/300, -2f/300, -2f/300);
        var clip = new Matrix4f();
        var handProjection = ScopeProjection.lastOf(
                ((GameRendererProjectionAccessor)(Object) mc.gameRenderer).tacz$getHudProjectionBuffer());
        // 手部投影还没被算出来过（首帧、或窗口刚变化）：本帧不画，下一帧就有镜像了。
        if (handProjection == null) return;
        clip.set(handProjection);
        // The hand pose includes the inverse camera basis. Match the actual GPU transform
        // (and the ocular mask's submit-time capture); pose alone is not camera-local.
        // Only the CPU projection needs this factor. The collector applies ModelView at draw time.
        clip.mul(new Matrix4f(RenderSystem.getModelViewMatrix())).mul(pose.last().pose());
        // Solve clipY(localY)/clipW(localY) = desired NDC, including tilted animated locators.
        double denominator = clip.m11() - projected * clip.m13();
        if (Math.abs(denominator) < 1e-7) return;
        float y = (float)((projected * clip.m33() - clip.m31()) / denominator);
        float w = clip.m33() + y * clip.m13();
        if (!Float.isFinite(y) || Math.abs(y) > 1000 || !(w > .0001f)) return;
        // Do not lose a subpixel line completely on a small window. Keep the pack scale and the
        // stroke proportional to the box, enlarging only the minimum visible footprint.
        float px = (float)(w / Math.abs(denominator) * 2 / Math.max(1, mc.getWindow().getHeight()));
        float half = Math.max(3f, px * 2) * settings.getImpactScale();
        float line = Math.min(half, Math.max(px, half / 3));
        int color = settings.getImpactColor();
        var texture = BeamRenderer.LASER_BEAM_TEXTURE;
        SubmitNodeCollector.CustomGeometryRenderer box = (frozen, vertices) -> drawBox(frozen, vertices, y, half, line, color, overlay);
        if (layer == Layer.FINAL_APERTURE) {
            // The lens composite replaces the earlier hand colour, including this box. Queue
            // the four frozen quads, not another hit test or a mutable locator, above the ink.
            ScopeFinalOverlayState.queueReadout(pose, ScopeRenderTypes.finalReadout(texture), box);
        } else {
            collector.submitCustomGeometry(pose, layer == Layer.APERTURE ? ScopeRenderTypes.visibleReticle(texture)
                    : RenderTypes.entityTranslucentEmissive(texture), box);
        }
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, float y, float h, float line, int color, int overlay) {
        quad(pose, out, -h, y-h, h, y-h+line, color, overlay);
        quad(pose, out, -h, y+h-line, h, y+h, color, overlay);
        quad(pose, out, -h, y-h+line, -h+line, y+h-line, color, overlay);
        quad(pose, out, h-line, y-h+line, h, y+h-line, color, overlay);
    }
    private static void quad(PoseStack.Pose p, VertexConsumer v, float l, float t, float r, float b, int color, int overlay) {
        vertex(p, v, l, t, color, overlay); vertex(p, v, l, b, color, overlay);
        vertex(p, v, r, b, color, overlay); vertex(p, v, r, t, color, overlay);
    }
    private static void vertex(PoseStack.Pose p, VertexConsumer v, float x, float y, int color, int overlay) {
        v.addVertex(p.pose(), x, y, 0).setColor(color).setUv(.5f,.5f).setOverlay(overlay).setLight(15728880).setNormal(p, 0, 0, 1);
    }
}
