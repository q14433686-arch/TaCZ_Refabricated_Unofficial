package com.tacz.guns.compat.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 瞄具专用 Iris 管线的「维度 id」与<b>预热</b>（26.2 同名类的移植；裁剪版）。
 *
 * <h2>为什么需要一套专用管线</h2>
 * Iris 的所有「上一帧」类状态都是<b>读一次推进一次</b>的
 * （{@code MatrixUniforms$Previous}、{@code CameraPositionTracker} 等）。
 * 二次渲染在光影下一帧要跑两遍完整管线，主画面那一遍拿到的「上一帧」就会是
 * <b>本帧镜内那一遍</b>的值 —— 所有靠时域重投影的效果全部失准，26.2 实测三种表现同源：
 * 整屏拖影、体积云噪点闪烁、开镜时镜外整屏发糙（曾被误认成「锐化溢出」）。
 *
 * <h2>做法：借 Iris 自己的按维度分管线机制</h2>
 * Iris 的管线是<b>按维度缓存</b>的（{@code PipelineManager.pipelinesPerDimension}）。
 * 只要在镜内那一遍期间让 {@code Iris.getCurrentDimension()} 返回一个专用 id
 * （见 {@code IrisScopeDimensionMixin}），Iris 就会为它单独建一套管线 ——
 * 独立的 {@code RenderTargets}、独立的程序、因而<b>独立的那一整族 previous uniform 实例</b>。
 * 我们<b>不自己持有</b>那套管线：它躺在 Iris 的 map 里，切维度／重载光影包时
 * {@code PipelineManager.destroyPipeline()} 会把它一并回收，不漏显存。
 *
 * <h2>为什么要预热，而不是等第一次开镜时懒加载</h2>
 * Iris 的管线是用到才建的。不预热的话，那套瞄具管线会在<b>第一次开镜的那一帧</b>才开始
 * 编译整个 shaderpack；而且那一刻正好落在镜内那一遍<b>里面</b>，等于在一帧的中途做重活
 * （{@code preparePipeline} 会 reset 全局帧计数/计时器，时域效果当场错乱）。把它挪到
 * 进世界后的<b>普通帧</b>帧首（见 {@code GameRendererMixin} 的 render HEAD 调用点），
 * 卡顿就从「战斗中第一次举镜」变成「进世界后一次性」。
 *
 * <h3>预热之后要把「当前管线」指回去</h3>
 * {@code preparePipeline} 除了建/取，还会把当前管线指向刚取到的那套。预热完必须再用
 * <b>真实维度</b>调一次把它指回主管线（缓存命中，不会重建）。漏掉这步，整帧主画面都会
 * 用瞄具管线渲染。所以指回动作与建栈同窗口、失败也绝不能跳过。
 *
 * <p>全程反射，Iris 不在或结构变了就安静放弃（退回懒加载/共用管线，只差体验不崩）。</p>
 *
 * <h2>相对 26.2 母版的裁剪</h2>
 * <ul>
 *   <li>Voxy 第二套渲染栈（{@code VoxyScopePipelineCompat}）：本线没有 Voxy compat，
 *       镜内 LOD 远景暂不可画（缺它时 Voxy 的镜内行为未定义，待有环境再补）；</li>
 *   <li>{@code ScopePipShadowScale} 阴影降采样与空闲释放（FPS 衰减调查线）：未随移植，
 *       性能杠杆当前只有 {@code ScopePipRerenderInterval}。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class IrisScopePipelineCompat {

    private static final String NAMESPACED_ID = "net.irisshaders.iris.shaderpack.materialmap.NamespacedId";

    private static Object scopeDimensionId;
    private static boolean idResolveFailed;

    private static Method getPipelineManager;
    private static Method getPipelineNullable;
    private static Method preparePipeline;
    private static Method getCurrentDimension;
    private static Field pipelinesMapField;
    private static boolean handlesResolved;
    private static boolean handlesFailed;

    /** 已经为哪一套主管线预热过。主管线换了（重载光影包/切维度）就要重来。 */
    private static Object prewarmedAgainst;
    private static boolean loggedPrewarm;
    /** Voxy 那一套是否已经尘埃落定（建好了，或确定用不上）。稳态快速路径就看它。 */
    private static boolean voxyStackSettled;

    /**
     * 是否正处在「瞄具那套 Iris 管线的构造过程」之中。
     *
     * <p>只有这一小段窗口里，{@code PackShadowDirectives.getResolution()} 的返回值
     * 才会决定<b>瞄具管线</b>那张阴影贴图的尺寸（{@code IrisShadowResolutionMixin}
     * 据此改小）。窗口之外必须恢复原值，否则会把主画面的阴影一起改小。</p>
     */
    private static volatile boolean buildingScopePipeline;

    /** 由 {@code IrisShadowResolutionMixin} 查询。 */
    public static boolean isBuildingScopePipeline() {
        return buildingScopePipeline;
    }

    /** 「释放失败过就不再重试」的一次性标志：释放不成功就保持管线活着，别每帧折腾。 */
    private static boolean releaseFailed;

    /**
     * 当前活着的瞄具管线是按哪个 ScopePipShadowScale 建的；NaN = 还没建过。
     *
     * <p>阴影贴图分辨率是管线<b>构造时</b>读 {@code PackShadowDirectives.getResolution()}
     * 一次性定死的，此后 ShadowRenderTargets / ShadowRenderer 全用这份快照。所以配置改了
     * 而管线还活着 = 改了等于没改。记下建管线时的值，{@link #prewarmIfNeeded()} 每帧比对，
     * 变了就销毁重建，让旋钮热生效。</p>
     */
    private static double appliedShadowScale = Double.NaN;

    /**
     * 本次 preparePipeline 构建期间，IrisShadowResolutionMixin 是否真的拦到过
     * {@code getResolution()}。该 mixin 是 {@code require = 0} 的软注入 ——
     * Iris 内部类名/方法变了它会<b>静默</b>失效，游戏照常跑、缩放悄悄不生效。
     * 构建前清零、构建后检查，把静默失效变成一行明确的告警。
     */
    private static volatile boolean shadowHookRanDuringBuild;

    /** 由 {@code IrisShadowResolutionMixin} 在构造窗口内拦到 getResolution() 时回调。 */
    public static void noteShadowResolutionIntercepted() {
        shadowHookRanDuringBuild = true;
    }

    private static double wantedShadowScale() {
        return RenderConfig.SCOPE_PIP_SHADOW_SCALE == null
                ? 1.0d : RenderConfig.SCOPE_PIP_SHADOW_SCALE.get();
    }

    /**
     * {@code LevelExtractor.allChanged()} 真的执行了 —— 也就是 Voxy 刚把整个
     * {@code VoxyRenderSystem} 拆了重建（改区块视距、F3+A、切资源包都会走到这里）。
     *
     * <p>光靠 {@link #prewarmIfNeeded()} 是发现不了的：它的稳态快速路径盯的是
     * <b>Iris 主管线</b>有没有换人，而 {@code allChanged()} 根本不碰 Iris 管线，
     * 于是 {@code voxyStackSettled} 一直是 true，逐帧直接返回，
     * 永远不会去问一句「Voxy 还是原来那个吗」。必须由这条事件打破快速路径。</p>
     */
    public static void onLevelRendererReload() {
        // 顺序：先把已经失效的那一套还回去，再把状态机打回「需要重新检查」。
        com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.onRendererRebuilt();
        voxyStackSettled = false;
    }

    private IrisScopePipelineCompat() {
    }

    /**
     * 时域隔离开关（{@code ScopePipIsolatePipeline}）。配置未加载时按开启处理，
     * 与其它 ScopePip 配置的 null 兜底方向一致。
     */
    public static boolean isolatePipelineEnabled() {
        return RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE == null
                || RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE.get();
    }

    /**
     * 瞄具那套管线用的维度 id。
     *
     * @return Iris 的 {@code NamespacedId} 实例；拿不到时返回 {@code null}
     *         （此时不做隔离，退回与主画面共用管线）
     */
    public static Object scopeDimensionId() {
        if (scopeDimensionId != null || idResolveFailed) {
            return scopeDimensionId;
        }
        try {
            Class<?> cls = Class.forName(NAMESPACED_ID);
            scopeDimensionId = cls.getConstructor(String.class, String.class)
                    .newInstance(GunMod.MOD_ID, "scope_pip");
        } catch (Throwable t) {
            idResolveFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Could not build the scope pipeline's dimension id; the "
                    + "scope pass will share the main shader pipeline.", t);
        }
        return scopeDimensionId;
    }

    private static boolean resolveHandles() {
        if (handlesResolved) {
            return !handlesFailed;
        }
        handlesResolved = true;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            getPipelineManager = iris.getMethod("getPipelineManager");
            getCurrentDimension = iris.getMethod("getCurrentDimension");
            Class<?> manager = getPipelineManager.getReturnType();
            getPipelineNullable = manager.getMethod("getPipelineNullable");
            preparePipeline = manager.getMethod("preparePipeline", Class.forName(NAMESPACED_ID));
            // Iris 的 PipelineManager 里只有一个 Map 字段（pipelinesPerDimension），
            // 这里按类型找而不是按名找，名字变了也不至于抓空。
            for (Field field : manager.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    pipelinesMapField = field;
                    break;
                }
            }
            if (pipelinesMapField != null) {
                pipelinesMapField.setAccessible(true);
            }
        } catch (Throwable t) {
            handlesFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Could not resolve Iris' pipeline manager; the scope "
                    + "pipeline will be built lazily on first aim (expect one stutter).", t);
        }
        return !handlesFailed;
    }

    /**
     * 当前活着的瞄具管线若存在则整份销毁，释放它占用的<b>全部</b> GPU 资源
     * （colortex/gbuffer/阴影图/SSBO/程序，见 {@code IrisRenderingPipeline#destroy}）。
     *
     * <p>两个用途：①{@code ScopePipShadowScale} 改动后的热重建（旧管线必须先死，
     * 新值才能在构造时被读走）；②空闲释放实验（{@code ScopePipReleaseIdlePipeline}）——
     * 26.2 查明「光影下开镜帧率自首次 ADS 起持续衰减、重进存档重置」强烈指向
     * <b>每 scope pass 在瞄具管线的保留 GPU 状态里累积</b>（CPU 侧探针全平），
     * 空闲时整份销毁、下次开镜由 {@link #prewarmIfNeeded()} 重建即可清零。</p>
     *
     * <p>只在「不在镜内那一遍、且处于帧内世界渲染前的安全位置」调用。销毁后顺手失效
     * 预热状态。</p>
     *
     * @return 真的销毁了瞄具管线时返回 true
     */
    public static boolean releaseScopePipelineIfPresent() {
        if (releaseFailed) {
            return false;
        }
        if (!FabricLoader.getInstance().isModLoaded("iris") || !IrisCompat.isUsingRenderPack()) {
            return false;
        }
        Object id = scopeDimensionId();
        if (id == null || !resolveHandles() || pipelinesMapField == null) {
            return false;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            if (manager == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> pipelines = (Map<Object, Object>) (Map<?, ?>) pipelinesMapField.get(manager);
            Object scope = pipelines.get(id);
            if (scope == null) {
                return false;
            }
            scope.getClass().getMethod("destroy").invoke(scope);
            pipelines.remove(id);
            // PipelineManager.pipeline 若正指着被销毁的那套，指回主管线，
            // 别让后续任何 getPipelineNullable() 的消费者拿到已释放的管线。
            Object current = getPipelineNullable.invoke(manager);
            if (current == scope) {
                Object real = getCurrentDimension.invoke(null);
                if (real != null) {
                    preparePipeline.invoke(manager, real);
                }
            }
            // 预热状态随这套管线一起失效；Voxy 第二套栈绑的就是这套管线，一并失效。
            prewarmedAgainst = null;
            appliedShadowScale = Double.NaN;
            voxyStackSettled = false;
            com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.onRendererRebuilt();
            GunMod.LOGGER.info("[TACZ Scope] Released the idle scope-pass Iris pipeline to reclaim GPU memory.");
            return true;
        } catch (Throwable t) {
            releaseFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Failed to release the idle scope pipeline; keeping it alive "
                    + "for this session (releasing will not be retried).", t);
            return false;
        }
    }

    /**
     * 若还没预热过，就在<b>当前这一帧的安全位置</b>把瞄具管线建好。
     *
     * <p>由 {@code GameRendererMixin} 的 render HEAD 调用 —— 那里在世界渲染<b>之前</b>，
     * 不在任何 render pass 内，也不在我们的镜内那一遍里（26.2 用 extract HEAD，语义同类：
     * 都是「帧内世界渲染开始前的空档」；本线该注入点已随 PIP 接线存在并实机验证）。</p>
     */
    public static void prewarmIfNeeded() {
        if (!ScopePipRerender.rerenderMode() || !isolatePipelineEnabled()) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("iris") || !IrisCompat.isUsingRenderPack()) {
            return;
        }
        Object id = scopeDimensionId();
        if (id == null || !resolveHandles()) {
            return;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            if (manager == null) {
                return;
            }
            Object mainPipeline = getPipelineNullable.invoke(manager);
            if (mainPipeline == null) {
                // 主管线还没建起来（刚进世界）。等它先建好，下一帧再说 ——
                // 抢在它前面预热会让「当前管线」指向瞄具那套，把这一帧的主画面画错。
                return;
            }
            // 【ScopePipShadowScale 热生效】阴影分辨率是管线构造时一次性定死的 ——
            // 配置变了而旧管线还活着，就先销毁它，让下面的重建按新值走。
            // 放在快速路径之前：否则「已就绪」直接短路，改配置永远等到下次进世界才生效。
            if (prewarmedAgainst == mainPipeline
                    && !Double.isNaN(appliedShadowScale)
                    && Math.abs(appliedShadowScale - wantedShadowScale()) > 1.0e-3) {
                GunMod.LOGGER.info("[TACZ Scope] ScopePipShadowScale changed ({} -> {}); rebuilding the "
                                + "scope pipeline so the new shadow map size takes effect.",
                        appliedShadowScale, wantedShadowScale());
                if (releaseScopePipelineIfPresent()) {
                    // 释放成功已把 prewarmedAgainst/appliedShadowScale 清零，
                    // 下面自然落进慢路径按新值重建。
                } else {
                    // 释放失败（或该路径被熔断）：把「已应用值」改记为目标值，
                    // 停止重试 —— 否则这个分支每帧都进，日志刷屏。
                    // 旧管线继续用旧阴影尺寸，等下次自然重建（切维度/重载光影）再生效。
                    appliedShadowScale = wantedShadowScale();
                }
            }
            // 【稳态快速路径】本方法逐帧都会被调到，所以「已经全部就绪」这条必须最便宜。
            // 主管线没换人、且 Voxy 那套也建好了 —— 直接回，不去碰任何 Voxy 反射。
            if (prewarmedAgainst != mainPipeline) {
                voxyStackSettled = false;
            }
            if (prewarmedAgainst == mainPipeline && voxyStackSettled) {
                return;
            }

            // 慢路径：只有还没就绪时才走。Voxy 的第二套栈可能要等它自己先建好，
            // 所以这里每帧问一次 —— 光看「管线预热过没有」会漏掉
            // 「预热那一刻 Voxy 还没就绪」的情况。
            Object voxy = com.tacz.guns.compat.voxy.VoxyCompat.renderSystem();
            boolean voxyUsable = voxy != null
                    && com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.isAvailable();
            boolean needVoxyStack = voxyUsable
                    && !com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.isBuiltFor(voxy);
            if (prewarmedAgainst == mainPipeline && !needVoxyStack) {
                // 没装 Voxy、或它那套用不上 —— 记下来，以后走快速路径。
                voxyStackSettled = true;
                return;
            }
            Object realDimension = getCurrentDimension.invoke(null);
            // 「这次是真构建还是缓存命中」—— 决定下面对阴影 mixin 的核验是否有意义：
            // 缓存命中不会读 getResolution()，拿它去核验只会误报。
            boolean wasAbsent = pipelinesMapField != null
                    && !((Map<?, ?>) pipelinesMapField.get(manager)).containsKey(id);
            // 打开窗口：把瞄具那套设成「当前管线」。第一次会真的编译，之后是缓存命中。
            //
            // 这个窗口<b>同时</b>是「瞄具管线正在构造」的唯一时机 —— 阴影贴图的分辨率
            // 就是在构造里读 PackShadowDirectives.getResolution() 定下来的，且此后由
            // 采样器一路捕获使用。所以要给镜内那一遍配一张更小的阴影图，只有在这里做
            // 才来得及。见 IrisShadowResolutionMixin。
            buildingScopePipeline = true;
            shadowHookRanDuringBuild = false;
            try {
                preparePipeline.invoke(manager, id);
                try {
                    // 【只能在这个窗口里建】Voxy 的 RenderPipelineFactory 取的正是
                    // 「当前管线」，错过这里就会绑到主管线上，等于白建。
                    if (needVoxyStack) {
                        com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.ensureBuilt(voxy);
                    }
                } catch (Throwable ignored) {
                    // Voxy 那侧失败只影响镜内有没有 LOD，不该拖累管线预热本身
                }
            } finally {
                buildingScopePipeline = false;
            }
            if (wasAbsent) {
                appliedShadowScale = wantedShadowScale();
                // 【把静默失效变成明确告警】IrisShadowResolutionMixin 是 require=0 的
                // 软注入，Iris 改内部类名它就悄悄不生效 —— 缩放旋钮随之变成空转。
                // 真构建过却一次都没拦到 getResolution()，就是这种情况
                //（阴影被 pack 完全禁用时构造器也不会读它，那时缩放本来就无意义）。
                if (!shadowHookRanDuringBuild && wantedShadowScale() < 0.999d) {
                    GunMod.LOGGER.warn("[TACZ Scope] ScopePipShadowScale is set to {} but the shadow "
                                    + "resolution hook never ran while building the scope pipeline. Either "
                                    + "this pack has shadows disabled (then the knob is moot), or the Iris "
                                    + "internals moved and the mixin no longer applies -- the scope pass is "
                                    + "using the pack's FULL shadow resolution.",
                            wantedShadowScale());
                }
            }
            try {
                // 【必须】把当前管线指回主管线，否则这一帧的主画面会用瞄具那套渲染。
                // 放 finally：上面抛了也绝不能把「当前管线」留在瞄具那套上。
                if (realDimension != null) {
                    preparePipeline.invoke(manager, realDimension);
                }
            } finally {
                prewarmedAgainst = mainPipeline;
            }
            if (!loggedPrewarm) {
                loggedPrewarm = true;
                GunMod.LOGGER.info("[TACZ Scope] Pre-built the scope pass' Iris pipeline now, so the "
                        + "first time you aim does not stall while the shader pack compiles.");
            }
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ Scope] Failed to pre-build the scope pipeline; it will be built "
                    + "on first aim instead (expect one stutter).", t);
        }
    }
}
