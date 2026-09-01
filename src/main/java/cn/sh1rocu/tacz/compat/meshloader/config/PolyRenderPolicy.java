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

    /**
     * 【开镜距离补偿 —— 2026-09-02 实机回报】两道距离闸门都按<b>裸眼</b>距离
     * 调参，但开镜把远处物体的角尺寸放大了 Z 倍：4x 镜下 48 格的 poly 上限
     * 观感只剩 12 格、16 格全模豁免观感只剩 4 格 —— 举镜看到的掉落物/第三人称
     * mesh 枪几乎必然是立方体（「镜内还是未烘焙」的实机回报）。
     *
     * <p>「多远该有细节」本质是<b>角尺寸</b>判定：把阈值乘上当前放大倍数即恢复
     * 语义一致。渐变随开镜进度走、收镜自动回 1；经典整屏变焦与 PIP 二次渲染
     * 都适用（镜内那一遍复用 extract 阶段的同一批提交节点，闸门只在提交时
     * 过一次，所以必须在提交侧补偿而不能在渲染侧）。</p>
     *
     * <p>成本封顶可控：Z 倍距离内的枪才会提交 poly，且世界 GPU 路径
     * （MeshGpuWorld）已把每枪成本降到 O(骨骼)；collector 回退档在
     * 16·Z 格外仍受顶点预算保护。</p>
     */
    private static double detailZoom() {
        try {
            return com.tacz.guns.client.render.scope.ScopePipRenderer.currentDetailZoom();
        } catch (Throwable t) {
            // scope 线可用性问题绝不许连坐 mesh 距离闸门：退回裸眼语义。
            return 1.0;
        }
    }
}
