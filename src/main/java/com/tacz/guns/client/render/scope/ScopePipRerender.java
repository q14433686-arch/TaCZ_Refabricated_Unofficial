package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
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
 * <h2>1.21.11 与 26.1.2 的差异（javap 逐项核实）</h2>
 * <ul>
 *   <li>1.21.11 是 10 参 {@code LevelRenderer#renderLevel(allocator, deltaTracker, blockOutline,
 *       camera, viewMatrix, projectionMatrix, cullingMatrix, fogBuffer, fogColor, renderSky)}，
 *       投影/视图是纯 CPU {@link Matrix4f} 参数；26.1.2 是 9 参
 *       {@code renderLevel(allocator, deltaTracker, blockOutline, cameraState, viewMatrix,
 *       fogBuffer, fogColor, renderSky, chunkSections)} —— 相机与裁剪锥都在
 *       {@link CameraRenderState} 里，<b>没有投影矩阵参数</b>。</li>
 *   <li>1.21.11 的 {@code PerspectiveProjectionMatrixBuffer} 在 26.1.2 不存在；等价物是
 *       {@link ProjectionMatrixBuffer#getBuffer(Matrix4f)} —— 内部 Std140 打包 +
 *       {@code CommandEncoder.writeToBuffer} 上传，再
 *       {@code RenderSystem.setProjectionMatrix(slice, PERSPECTIVE)}。</li>
 *   <li>基准 FOV 从 {@code cameraState.projectionMatrix}（26.1.2 由 GameRenderer 每帧写入的
 *       {@code Matrix4f} 公有字段）的 m11 反解；远平面取 {@code cameraState.depthFar}（26.1.2
 *       无 {@code GameRenderer#getDepthFar}）。窄投影按下述双通道同时生效：
 *       {@code RenderSystem} 投影槽（着色器 UBO 消费）+ 临时改写
 *       {@code cameraState.projectionMatrix}（等价于 1.21.11 把窄矩阵当第 6 参传入；
 *       两处都在 finally 里还原）。</li>
 *   <li>裁剪锥（{@code cameraState.cullFrustum}）保持宽视场不动 —— 与 1.21.11 让
 *       {@code cullingMatrix} 参数保持宽视场同一语义（宽视锥 = 超集，结果正确，只稍费一点）。</li>
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
 * 这样避开了 FrameGraph 的 {@code LevelTargetBundle}/{@code ResourceHandle}
 * 输出重定向（那是 B2 的事），换来的限制是镜内那遍仍以<b>主目标全分辨率</b>渲染，
 * {@link #resolutionScale()} 当前只读不生效，等到 B2 重定向落地才真正降采样。</p>
 *
 * <p><b>已知的运行时风险（编译通过 ≠ 运行安全）</b>：一帧内驱动两次
 * {@code LevelRenderer#renderLevel} 会推进两遍区块编译/实体提取等逐帧状态，
 * 26.2 已经记录过「镜外实体偶发消失」且未查明根因（详见其类注释第三条）。
 * 26.2 为此默认关闭本开关；本移植同样默认关闭，并把提交节点保留等防护留给后续阶段。
 * 另一未验证点：26.1.2 的 {@code LevelRenderer#renderLevel} 内部还有
 * {@code LevelRenderState#reset()} 与 FrameGraph 执行，二次执行对逐帧状态的影响同样
 * 只能在实机上检验。</p>
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
    private static ProjectionMatrixBuffer projectionBuffer;
    /** 窄投影矩阵，复用避免每帧分配。 */
    private static final Matrix4f NARROW_MATRIX = new Matrix4f();
    /** cameraState.projectionMatrix 的暂存（窄那遍前后交换还原用）。 */
    private static final Matrix4f SAVED_CAMERA_PROJECTION = new Matrix4f();

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
                                          CameraRenderState cameraState,
                                          Matrix4fc viewMatrix,
                                          GpuBufferSlice fogBuffer,
                                          Vector4f fogColor,
                                          boolean renderSky,
                                          ChunkSectionsToRender chunkSectionsToRender) {
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
        // 26.1.2 的投影不在参数表里，源是 cameraState.projectionMatrix（GameRenderer 每帧写入）。
        float m11 = cameraState.projectionMatrix.m11();
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

        // 近平面取 1.21.11/26.2 的字面量 0.05f（vanilla getProjectionMatrix 字节码里的 ldc 常量），
        // 远平面取当前帧的 cameraState.depthFar（26.1.2 无 getDepthFar()，字节码核实）。
        float aspect = (float) main.width / (float) main.height;
        float depthFar = cameraState.depthFar;
        NARROW_MATRIX.identity().perspective((float) Math.toRadians(narrowFov), aspect, PROJECTION_Z_NEAR, depthFar);
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("tacz scope pip");
        }

        // 存档投影。刻意不用 RenderSystem.backup/restoreProjectionMatrix()（共用单槽位，见
        // 26.2 的同名注释）；与 ScopeFinalOverlayState 同款手工存取。26.1.2 额外要暂存
        // cameraState.projectionMatrix 本体（见 finally）。
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        SAVED_CAMERA_PROJECTION.set(cameraState.projectionMatrix);

        scopePassActive = true;
        try {
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(NARROW_MATRIX), ProjectionType.PERSPECTIVE);
            // 26.1.2 的 renderLevel 没有投影参数：着色器走 RenderSystem 投影槽（上一行），
            // 其余消费点读 cameraState.projectionMatrix —— 临时改写成窄矩阵，等价于 1.21.11
            // 把窄矩阵当第 6 参传入 renderLevel。
            cameraState.projectionMatrix.set(NARROW_MATRIX);
            // 镜内那遍：不画方块高亮线框（屏幕空间描边在镜内无意义）；viewMatrix 保持
            // 宽视场（宽视锥裁剪 = 超集，结果正确，只稍费一点），cullFrustum 同理不动。
            levelRenderer.renderLevel(allocator, deltaTracker, false, cameraState,
                    viewMatrix, fogBuffer, fogColor, renderSky, chunkSectionsToRender);
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
            cameraState.projectionMatrix.set(SAVED_CAMERA_PROJECTION);
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
        }
    }

    /** 供 {@code ScopePipRenderState#worldZoomTarget()} 查询：二次渲染下世界恒 1×。 */
    public static boolean worldZoomForcedToOne() {
        return rerenderMode() && !failed;
    }
}
