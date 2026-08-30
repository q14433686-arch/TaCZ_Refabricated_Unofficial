package cn.sh1rocu.tacz.compat.meshloader.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * 按显示上下文决定要不要画 poly 层。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
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

    /**
     * 世界语境（第三人称/掉落物/展示台）的<b>近距离全模豁免</b>：
     * 相机距离小于 {@code MeshWorldFullDetailDistance} 时，
     * {@code MeshWorldMaxVertices} 顶点预算不生效——眼前的枪永远画全模，
     * 预算只保护远处/密集场景。0 = 关闭豁免（回到旧的一刀切预算）。
     *
     * <p>距离与 {@link #withinDistance} 同源：pose 平移量即相机空间距离。
     * {@code poseStack == null} 时无从测距，返回 false（预算照常生效）。</p>
     */
    public static boolean withinFullDetailDistance(PoseStack poseStack) {
        double distance = MeshyConfig.WORLD_FULL_DETAIL_DISTANCE.get();
        if (distance <= 0 || poseStack == null) {
            return false;
        }
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
        Matrix4f matrix = poseStack.last().pose();
        double dx = matrix.m30();
        double dy = matrix.m31();
        double dz = matrix.m32();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }
}
