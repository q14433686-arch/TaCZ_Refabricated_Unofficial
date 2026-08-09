package cn.sh1rocu.tacz.compat.meshloader.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * poly_mesh 渲染策略：按上下文决定是否绘制 poly 层。
 *
 * <p>26.2 无 VBO 几何缓存，poly 顶点每帧由 CPU 重建 —— 高面数模型是主要
 * 开销。策略把 poly 绘制裁剪到必要场合（第一人称永远全量；阴影 pass
 * 默认跳过；世界上下文按距离裁剪；预览上下文可配置），
 * 与移植版自身的 LOD/光影降级思路一致。</p>
 */
public final class PolyRenderPolicy {

    private PolyRenderPolicy() {
    }

    /**
     * @param transformType 当前提交上下文；null 视为世界上下文。
     * @param poseStack     当前提交矩阵（用于距离判断）。
     */
    public static boolean shouldRenderPoly(ItemDisplayContext transformType, PoseStack poseStack) {
        if (!MeshyConfig.ENABLE_MESH.get()) {
            return false;
        }
        // 阴影 pass：默认跳过（立方体阴影足够；上游同款策略）。
        // IrisCompat 内部对未装 Iris 的环境直接返回 false，可无条件调用。
        if (IrisCompat.isRenderShadow() && !MeshyConfig.POLY_IN_SHADOW.get()) {
            return false;
        }
        if (transformType == null) {
            return withinDistance(poseStack);
        }
        // 第一人称：永远全量。
        if (transformType.firstPerson()) {
            return true;
        }
        // GUI/FIXED/HEAD 属于「预览/展示」上下文（工作台预览、雕像、头部展示）。
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            return MeshyConfig.POLY_IN_PREVIEW.get();
        }
        // 其余（THIRD_PERSON_* / GROUND / NONE 掉落物/子弹等）：距离裁剪。
        return withinDistance(poseStack);
    }

    /** 以提交矩阵的平移分量（相对相机）做距离判断。0 = 不限制。 */
    private static boolean withinDistance(PoseStack poseStack) {
        double distance = MeshyConfig.MAX_RENDER_DISTANCE.get();
        if (distance <= 0) {
            return true;
        }
        if (poseStack == null) {
            return true;
        }
        Matrix4f matrix = poseStack.last().pose();
        double dx = matrix.m30();
        double dy = matrix.m31();
        double dz = matrix.m32();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }
}
