package cn.sh1rocu.tacz.compat.meshloader.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * poly_mesh 渲染策略：按上下文决定是否绘制 poly 层。
 */
public final class PolyRenderPolicy {

    private PolyRenderPolicy() {
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

    private static boolean withinDistance(PoseStack poseStack) {
        double distance = MeshyConfig.MAX_RENDER_DISTANCE.get();
        if (distance <= 0 || poseStack == null) {
            return true;
        }
        Matrix4f matrix = poseStack.last().pose();
        double dx = matrix.m30();
        double dy = matrix.m31();
        double dz = matrix.m32();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }
}
