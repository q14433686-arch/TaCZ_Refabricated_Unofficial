package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * 瞄准镜「镜内二次渲染」：用窄 FOV 把世界<b>真画一遍</b>，得到原生分辨率的镜内画面。
 *
 * <p>与默认的「屏幕空间重投影」（{@link ScopePipRenderState}，只放大主画面中心一小块）
 * 不同，这条路每帧多跑一遍完整的世界渲染，镜内像素是窄 FOV 下真实画出来的，
 * 没有「镜内分辨率上限 = 屏幕分辨率 ÷ 倍率」那条天花板。代价是每帧一整个世界的额外渲染，
 * 因此由 {@code RenderConfig.SCOPE_PIP_RERENDER} 玩家自选，默认关闭。</p>
 *
 * <h2>1.21.11 与 26.2 参考实现的差异（javap 逐项核实）</h2>
 * <ul>
 *   <li>26.2 用 8 参 {@code LevelRenderer#render(...)} + {@code CameraRenderState}；
 *       1.21.11 是 10 参 {@code LevelRenderer#renderLevel(allocator, deltaTracker, blockOutline,
 *       camera, viewMatrix, projectionMatrix, cullingMatrix, fogBuffer, fogColor, renderSky)}，
 *       投影/视图是纯 CPU {@link Matrix4f} 参数，相机状态里不再存投影。</li>
 *   <li>26.2 的 {@code ProjectionMatrixBuffer}+{@code Projection} 在本版本不存在；
 *       等价物是 {@link PerspectiveProjectionMatrixBuffer#getBuffer(Matrix4f)} ——
 *       内部 Std140 打包 + {@code CommandEncoder.writeToBuffer} 上传，再
 *       {@code RenderSystem.setProjectionMatrix(slice, PERSPECTIVE)}。</li>
 *   <li>离屏 target 等价物（26.2 的 {@code ScopePipTarget} 及其离屏 FBO）在 B1 用不到 ——
 *       采用「拷主目标」方案，窄 FOV 成品先画进主目标再拷走；真正的离屏重定向
 *       （{@code LevelTargetBundle} 替换）留给 B2，对应构造/格式差异届时再 javap 核实。</li>
 * </ul>
 *
 * <h2>B1 裁剪：拷贝主目标，不重定向</h2>
 * 本版本采用与 26.2 光影路径同款思路——镜内那遍照常画进<b>主目标</b>，
 * 画完立刻把成品拷进离屏纹理（{@link ScopePipRenderState#captureSceneFromMain}），
 * 再由 {@code GameRendererMixin} 让 vanilla 那遍从头再画一遍覆盖掉主目标。
 * 两遍都发生在同一帧内、交换缓冲之前，因此镜内那遍永远不会被呈现到屏幕上。
 * 这样避开了 1.21.11 FrameGraph 的 {@code LevelTargetBundle}/{@code ResourceHandle}
 * 输出重定向（那是 B2 的事），换来的限制是镜内那遍仍以<b>主目标全分辨率</b>渲染，
 * {@link #resolutionScale()} 当前只读不生效，等到 B2 重定向落地才真正降采样。</p>
 *
 * <p><b>已知的运行时风险（编译通过 ≠ 运行安全）</b>：一帧内驱动两次
 * {@code LevelRenderer#renderLevel} 会推进两遍区块编译/实体提取等逐帧状态，
 * 26.2 已经记录过「镜外实体偶发消失」且未查明根因（详见其类注释第三条）。
 * 26.2 为此默认关闭本开关；本移植同样默认关闭，并把提交节点保留等防护留给后续阶段。</p>
 */
public final class ScopePipRerender {
    private static final float PROJECTION_Z_NEAR = 0.05f;

    /** 一旦出过错就永久停用，避免每帧刷屏或反复抛异常。 */
    private static boolean failed = false;
    /** 本帧是否已产出可合成的镜内画面（窄 FOV 世界拷贝）。 */
    private static boolean sceneCaptured = false;
    /** 镜内那一遍是否正在执行（防重入）。 */
    private static boolean scopePassActive = false;

    @Nullable
    private static PerspectiveProjectionMatrixBuffer projectionBuffer;
    /** 窄投影矩阵，复用避免每帧分配。 */
    private static final Matrix4f NARROW_MATRIX = new Matrix4f();

    private static boolean loggedFirst;

    private ScopePipRerender() {
    }

    /** 是否走「二次渲染」而不是「屏幕空间重投影」。 */
    public static boolean rerenderMode() {
        return RenderConfig.SCOPE_PIP_RERENDER != null && RenderConfig.SCOPE_PIP_RERENDER.get();
    }

    /** 镜内离屏纹理相对主目标的分辨率比例。B1 尚未接线（见类注释），保留读取入口。 */
    public static double resolutionScale() {
        return RenderConfig.SCOPE_PIP_RESOLUTION_SCALE == null
                ? 0.75d : RenderConfig.SCOPE_PIP_RESOLUTION_SCALE.get();
    }

    /**
     * 镜内那一遍世界渲染是否正在执行。除防重入外，也给「按 pass 分流」的渲染闸门用：
     * 例如 poly_mesh 的 GPU 世界表在这一遍画但不清表（提交每帧只发生一次，清了主画面就没得画）。
     */
    public static boolean isInsideScopeLevelRender() {
        return scopePassActive;
    }

    /** 本帧是否有可用的镜内画面（供合成阶段与 FOV 让位查询）。 */
    public static boolean hasScene() {
        return sceneCaptured && !failed;
    }

    /** 合成倍率：镜内画面已是窄 FOV 真画，屏幕坐标与主画面一一对应，恒为 1。 */
    public static float compositeZoom() {
        return 1.0f;
    }

    /**
     * 镜内那遍世界渲染。由 {@code GameRendererMixin} 在
     * {@code GameRenderer#renderLevel} 里 {@code LevelRenderer#renderLevel} 那次调用之前注入；
     * 本方法先把世界用窄 FOV 画进主目标、拷走，随后 vanilla 那遍再用宽 FOV 重画覆盖。
     *
     * @return 是否执行了镜内那遍（调用方据以决定 scene 是否已就绪）
     */
    public static boolean renderScopeView(LevelRenderer levelRenderer,
                                          GraphicsResourceAllocator allocator,
                                          DeltaTracker deltaTracker,
                                          boolean blockOutline,
                                          Camera camera,
                                          Matrix4f viewMatrix,
                                          Matrix4f projectionMatrix,
                                          Matrix4f cullingMatrix,
                                          GpuBufferSlice fogBuffer,
                                          Vector4f fogColor,
                                          boolean renderSky) {
        sceneCaptured = false;
        if (failed || !rerenderMode() || scopePassActive) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        // B1 只支持无光影路径：光影下整条世界渲染走 Iris 自己的 colortex，
        // 主目标里没有窄 FOV 的成品可拷，这条路留到后续阶段。
        if (IrisCompat.isUsingRenderPack()) {
            return false;
        }
        // 与重投影共用同一道「PIP 是否本帧接管镜头」的闸门（含开镜进度/倍率/掩码通道）。
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (!ScopePipRenderState.suppressesWorldFovZoom(partialTicks)) {
            return false;
        }
        float magnification = ScopePipRenderState.currentZoom();
        if (magnification <= 1.0f) {
            return false;
        }

        // 从宽投影矩阵反解基准 FOV：m11 = 1/tan(fovY/2) 是恒等式，与纵横比/近远平面无关。
        float m11 = projectionMatrix.m11();
        if (!Float.isFinite(m11) || m11 <= 1.0e-4f) {
            return false;
        }
        double baseFov = Math.toDegrees(2.0 * Math.atan(1.0 / m11));
        double narrowFov = MathUtil.magnificationToFov(magnification, baseFov);
        if (!Double.isFinite(narrowFov) || narrowFov <= 0.0) {
            return false;
        }

        var main = mc.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return false;
        }

        // 近平面取 vanilla 字面量 0.05f（getProjectionMatrix 字节码里的 ldc 常量），远平面取当前深度。
        float aspect = (float) main.width / (float) main.height;
        float depthFar = mc.gameRenderer.getDepthFar();
        NARROW_MATRIX.identity().perspective((float) Math.toRadians(narrowFov), aspect, PROJECTION_Z_NEAR, depthFar);
        if (projectionBuffer == null) {
            projectionBuffer = new PerspectiveProjectionMatrixBuffer("tacz scope pip");
        }

        // 存档投影。刻意不用 RenderSystem.backup/restoreProjectionMatrix()（共用单槽位，见
        // 26.2 的同名注释）；与 ScopeFinalOverlayState 同款手工存取。
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();

        scopePassActive = true;
        try {
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(NARROW_MATRIX), ProjectionType.PERSPECTIVE);
            // 镜内那遍：不画方块高亮线框（屏幕空间描边在镜内无意义）；viewMatrix/cullingMatrix
            // 保持宽视场（宽视锥裁剪 = 超集，结果正确，只稍费一点）。
            levelRenderer.renderLevel(allocator, deltaTracker, false, camera,
                    viewMatrix, NARROW_MATRIX, cullingMatrix, fogBuffer, fogColor, renderSky);
            // 立刻拷走：紧随其后的 vanilla 那遍会整屏重画主目标。
            sceneCaptured = ScopePipRenderState.captureSceneFromMain(mc);
            if (sceneCaptured && !loggedFirst) {
                loggedFirst = true;
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP second-render pass active: {}x{} narrow-FOV world "
                                + "at {}x magnification (resolution scale {}x not yet wired).",
                        main.width, main.height, magnification, resolutionScale());
            }
            return sceneCaptured;
        } catch (Throwable e) {
            failed = true;
            sceneCaptured = false;
            GunMod.LOGGER.error("[TACZ Scope] Scope PIP second-render pass failed; rerender disabled, "
                    + "falling back to screen-space reprojection / whole-screen FOV zoom.", e);
            return false;
        } finally {
            scopePassActive = false;
            // 必须还原：留窄投影会让 vanilla 那遍的整个世界被放大 —— 正好是反过来的病。
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
        }
    }

    /** 供 {@code ScopePipRenderState#worldZoomTarget()} 查询：二次渲染下世界恒 1×。 */
    public static boolean worldZoomForcedToOne() {
        return rerenderMode() && !failed;
    }
}
