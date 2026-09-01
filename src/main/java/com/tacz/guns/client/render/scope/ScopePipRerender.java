package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.math.MathUtil;
import net.fabricmc.loader.api.FabricLoader;
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
 * 26.2 已经记录过「镜外实体偶发消失」且未查明根因（详见其类注释第三条）。26.2 为此默认关闭本开关，
 * 本移植同样默认关闭。</p>
 *
 * <p><b>26.1.2 那条「窄遍把一次性状态袋烧光」的防护，本世代结构上不需要</b>（2026-09-01 javap 实测，
 * 依据是 CI 编译类路径上的 {@code minecraft-merged-…-1.21.11-…jar}，不是照抄 26.2 的结论）：
 * 那边 26.1.2 是 {@code GameRenderer#extract → LevelRenderer#extractLevel} 先把实体/区块/雾写进
 * {@code LevelRenderState}，{@code renderLevel} 只消费并在尾部 {@code reset()} ⇒ 窄遍消费完，主遍拿到空袋，
 * 镜外实体与太阳/雾/天气一起消失。本世代没有这一步：{@code LevelRenderer} 里<b>没有</b> {@code extractLevel}
 * （{@code extractVisibleEntities} / {@code extractVisibleBlockEntities} / {@code extractBlockOutline} /
 * {@code extractBlockDestroyAnimation} 全是私有方法且以 {@code state.LevelRenderState} 为入参，在
 * {@code renderLevel} 内部各自调用），{@code GameRenderer} 侧只有 {@code renderLevel(DeltaTracker)} 与
 * 一个 {@code private extractCamera(float)} ⇒ 每次 {@code renderLevel} 自带提取，第二遍不会饿着第一遍之后
 * 的主遍。提交节点同理：{@code renderAllFeatures()} 自身以 {@code submitNodeStorage.clear()} 收尾
 * （见 {@code com.tacz.guns.mixin.client.FeatureRenderDispatcherMixin} 的字节码记录），
 * 所以 26.1.2 为防「主遍叠加重影」补的那次 {@code clear()} 对我们是空操作 ——
 * <b>不加，也不欠</b>。仍待实机的只有本段开头那条泛化的「双遍逐帧状态」风险，见
 * {@code docs/lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md} §1。</p>
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

    /** 镜内那一遍是否正在用<b>自己的</b> Iris 管线（由维度替换 mixin 查询；光影隔离生效时为 true）。 */
    public static boolean isScopePassIsolated() {
        return scopePassIsolated;
    }

    /** Sodium/Voxy 会在窄遍里绕过我们的投影通道：本线没有对应的 compat，装了就不放行光影窄遍。 */
    private static final boolean THIRD_PARTY_WORLD_RENDERER_PRESENT =
            FabricLoader.getInstance().isModLoaded("sodium")
                    || FabricLoader.getInstance().isModLoaded("embeddium")
                    || FabricLoader.getInstance().isModLoaded("rubidium")
                    || FabricLoader.getInstance().isModLoaded("voxelism");

    /**
     * 光影下二次渲染的安全前提：隔离开关开 + 玩家显式 opt-in 光影 PIP + Iris 的管线管理器反射得动
     * + 没装会绕道我们投影通道的世界渲染 mod。
     *
     * <p>失败方向一律是<b>硬拒</b>（旧 B1 语义），不是「放行但没隔离」：后者正是 26.1.2 实机查明的
     * 三症状（整屏拖影 / 体积云噪点 / 镜外发糙）的成因，而我们没有他们那套 Voxy 第二渲染栈与
     * Sodium 投影快照同步可兜。</p>
     */
    public static boolean shaderIsolateSafe() {
        if (!IrisScopePipelineCompat.isolatePipelineEnabled()) {
            return false;
        }
        if (RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS == null
                || !RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get()) {
            return false;
        }
        if (THIRD_PARTY_WORLD_RENDERER_PRESENT) {
            if (!loggedShaderRefusal) {
                loggedShaderRefusal = true;
                GunMod.LOGGER.info("[TACZ Scope] Scope re-render under a shader pack stays disabled because a "
                        + "Sodium/Voxy-family world renderer is installed: this branch has no projection "
                        + "snapshot sync / second render stack for it, and without those the lens would draw "
                        + "the world at the wrong FOV. Uninstall-free fix would be to port those two compat "
                        + "layers (see docs/lineage/SCOPE_PIP_SHADER_ISOLATION_PORT_2612_20260901.md).");
            }
            return false;
        }
        if (!IrisScopePipelineCompat.handlesAvailable()
                || IrisScopePipelineCompat.scopeDimensionId() == null) {
            if (!loggedShaderRefusal) {
                loggedShaderRefusal = true;
                GunMod.LOGGER.warn("[TACZ Scope] Could not reach Iris' pipeline manager, so the scope "
                        + "pass keeps refusing to run under a shader pack (no isolation without it).");
            }
            return false;
        }
        return true;
    }

    private static boolean loggedShaderRefusal;
    private static boolean scopePassIsolated;
    private static int idleReleaseFrames;

    /**
     * 帧内世界渲染<b>之前</b>的空档（{@code GameRendererMixin} 的 render HEAD）预热瞄具管线，
     * 并在开着空闲释放时数着帧把用不上的那套还回去。
     *
     * <p>判据与镜内那遍一致（二次渲染 + 光影 opt-in + 隔离前提），但<b>不看开镜进度</b> ——
     * 预热的全部意义就是赶在第一次开镜之前把 shaderpack 编译做完。空闲释放开着时，未开镜的
     * 那段不预热：预热会立刻重建刚释放的管线，等于白释放。</p>
     */
    public static void prewarmShaderPipelineIfNeeded() {
        if (failed || !rerenderMode()) {
            return;
        }
        if (!IrisCompat.isUsingRenderPack() || !shaderIsolateSafe()) {
            idleReleaseFrames = 0;
            return;
        }
        if (RenderConfig.SCOPE_PIP_RELEASE_IDLE_PIPELINE != null
                && RenderConfig.SCOPE_PIP_RELEASE_IDLE_PIPELINE.get()) {
            int delay = RenderConfig.SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES == null
                    ? 120 : RenderConfig.SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES.get();
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (!ScopePipRenderState.suppressesWorldFovZoom(partialTicks)) {
                if (++idleReleaseFrames >= delay) {
                    IrisScopePipelineCompat.releaseScopePipelineIfPresent();
                }
                return;
            }
            idleReleaseFrames = 0;
        }
        IrisScopePipelineCompat.prewarmIfNeeded();
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
        // 光影下的窄遍要放行，整套前提必须齐备（时域隔离可用 + 未装 Sodium/Voxy，见 shaderIsolateSafe）；
        // 任一条不满足就仍按旧 B1 语义硬拒：宁可不画，也不要画出一屏时域伪影。
        boolean irisPass = IrisCompat.isUsingRenderPack();
        if (irisPass && !shaderIsolateSafe()) {
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
        // 置位期间 IrisScopeDimensionMixin 会让 Iris 把「当前维度」答成 tacz:scope_pip，
        // 于是这一遍用的是它自己那套管线；出窗立刻清零，切世界时早已不复存在。
        scopePassIsolated = irisPass;
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
            scopePassIsolated = false;
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
