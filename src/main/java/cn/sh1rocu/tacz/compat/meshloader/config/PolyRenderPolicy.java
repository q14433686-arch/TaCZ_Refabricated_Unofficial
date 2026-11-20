package cn.sh1rocu.tacz.compat.meshloader.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * 按显示上下文决定要不要画 poly 层。
 */
public final class PolyRenderPolicy {

    private PolyRenderPolicy() {
    }

    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    public static int illuminatedLight(int ambientLight) {
        if (!MeshyConfig.POLY_ILLUMINATED_REAL_SKY.get() || !shadersActive()) {
            return FULL_BRIGHT_LIGHT;
        }
        return 15 << 4 | ((ambientLight >>> 20) & 0xF) << 20;
    }

    private static volatile Boolean shadersActive;

    public static void setShadersActive(boolean active) {
        shadersActive = active;
    }

    public static boolean shadersActive() {
        Boolean cached = shadersActive;
        return cached != null ? cached : IrisCompat.isUsingRenderPack();
    }

    public static boolean shouldRenderPoly(ItemDisplayContext transformType, PoseStack poseStack) {
        if (!MeshyConfig.ENABLE_MESH.get()) {
            return false;
        }
        if (IrisCompat.isRenderShadow() && !MeshyConfig.POLY_IN_SHADOW.get()) {
            return false;
        }
        if (transformType == null) {
            return withinDistance(poseStack);
        }
        if (transformType.firstPerson()) {
            return true;
        }
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            return MeshyConfig.POLY_IN_PREVIEW.get();
        }
        return withinDistance(poseStack);
    }

    public static boolean withinFullDetailDistance(PoseStack poseStack) {
        double distance = MeshyConfig.WORLD_FULL_DETAIL_DISTANCE.get();
        if (distance <= 0 || poseStack == null) {
            return false;
        }
        distance *= detailZoom();
        Matrix4f matrix = poseStack.last().pose();
        double dx = matrix.m30();
        double dy = matrix.m31();
        double dz = matrix.m32();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }

    private static boolean withinDistance(PoseStack poseStack) {
        double distance = MeshyConfig.MAX_RENDER_DISTANCE.get();
        if (distance <= 0 || poseStack == null) {
            return true;
        }
        distance *= detailZoom();
        Matrix4f matrix = poseStack.last().pose();
        double dx = matrix.m30();
        double dy = matrix.m31();
        double dz = matrix.m32();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }

    private static double detailZoom() {
        try {
            return com.tacz.guns.client.render.scope.ScopePipRenderer.currentDetailZoom();
        } catch (Throwable t) {
            return 1.0;
        }
    }
}
