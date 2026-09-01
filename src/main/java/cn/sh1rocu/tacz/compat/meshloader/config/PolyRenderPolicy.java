package cn.sh1rocu.tacz.compat.meshloader.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.LightTexture;
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

    /** 与 {@code PolyMeshModel.FULL_BRIGHT} / {@code BedrockPart#render} 的 15728880 同一个值。 */
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    /**
     * 「自发光」部件（骨骼名以 {@code _illuminated} 结尾）该用什么光照值。
     *
     * <p>上游语义是硬编码 {@code 0xF000F0}（block=15 且 sky=15）：{@code BedrockPart#render}
     * 与 {@code PolyMeshModel} 都是这个数。无光影下这是对的 —— 原版光照图把 block 列与 sky 列
     * <b>相乘</b>，sky 给 0 就基本全黑，所以想「不受环境光、永远看得见」必须两边都拉满。</p>
     *
     * <p>但光影包把 <b>sky 分量读成「这块表面看得见天空」</b>：常亮 15 等于告诉光影包
     * 「太阳/月亮永远照得到我」⇒ 屋顶、墙都遮不住，枪身在白天/夜里都按天空亮度被照明。
     * {@code MeshPolyIlluminatedRealSky} 打开且<b>装了光影包</b>时，sky 用环境真值、block 仍保 15
     * —— 洞里照样看得见，但不再声称自己晒得到太阳。无光影下逐字保持上游行为。</p>
     *
     * @param ambientLight 该次提交拿到的环境光照值（真值来源）
     */
    public static int illuminatedLight(int ambientLight) {
        if (!MeshyConfig.POLY_ILLUMINATED_REAL_SKY.get() || !shadersActive()) {
            return FULL_BRIGHT_LIGHT;
        }
        return LightTexture.pack(15, (ambientLight >>> 20) & 0xF);
    }

    /**
     * 光影包状态缓存，由 {@code ShaderStateTracker} 每帧（START 相位）写入。
     * null = 还没采过样（加载期首帧、或一个 mesh 模型都没注册过）：那种情况下退回直接查一次，
     * 而不是假设「没光影」—— 否则自发光部件会先按无光影烘一次，等状态翻转又要重烘。
     */
    private static volatile Boolean shadersActive;

    static boolean shadersActive() {
        Boolean cached = shadersActive;
        return cached != null ? cached : IrisCompat.isUsingRenderPack();
    }

    /** 只给 {@code ShaderStateTracker} 用。 */
    public static void setShadersActive(boolean active) {
        shadersActive = active;
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
     * 【开镜距离补偿 —— 2026-09-01 同步 26.2 {@code 08869095}】两道距离闸门都按<b>裸眼</b>距离
     * 调参，但开镜把远处物体的角尺寸放大了 Z 倍：4x 镜下 48 格的 poly 上限观感只剩 12 格、
     * 16 格全模豁免观感只剩 4 格 —— 举镜看到的掉落物/第三人称 mesh 枪几乎必然是立方体
     * （「镜内还是未烘焙」的实机回报）。
     *
     * <p>「多远该有细节」本质是<b>角尺寸</b>判定：把阈值乘上当前放大倍数即恢复语义一致。
     * 渐变随开镜进度走、收镜自动回 1；经典整屏变焦与 PIP 二次渲染都适用（镜内那一遍复用
     * extract 阶段的同一批提交节点，闸门只在提交时过一次，所以必须在提交侧补偿而不能在渲染侧）。</p>
     *
     * <p>成本封顶可控：Z 倍距离内的枪才会提交 poly，且世界 GPU 路径已把每枪成本降到
     * O(骨骼)；collector 回退档在 16·Z 格外仍受顶点预算保护。</p>
     */
    private static double detailZoom() {
        try {
            return com.tacz.guns.client.render.scope.ScopePipRenderState.currentDetailZoom();
        } catch (Throwable t) {
            // scope 线可用性问题绝不许连坐 mesh 距离闸门：退回裸眼语义。
            return 1.0;
        }
    }
}
