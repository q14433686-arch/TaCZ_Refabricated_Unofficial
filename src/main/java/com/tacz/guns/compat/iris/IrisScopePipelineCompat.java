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
 * 【光影下二次渲染的时域隔离】给镜内那一遍单配一套 Iris 管线，把它的时域状态与主画面隔开。
 *
 * <p>病根（26.1.2 实机查明、我方按源码同构推得）：一帧跑两遍完整管线，而 Iris 的所有
 * 「上一帧」类状态都是<b>读一次推进一次</b>的 —— 主画面那一遍读到的「上一帧」其实是我们
 * 镜内那一遍的值。三种表现同源：整屏拖影、体积云噪点闪烁、开镜时镜外整屏发糙（TAA 收不住
 * 随机采样）。关 pack 的 TAA 只能压第一条，因为病根不是 TAA 而是「每份上一帧被推进两次」。</p>
 *
 * <p>做法：借 Iris 自己的<b>按维度缓存管线</b>机制（{@code PipelineManager.pipelinesPerDimension}）。
 * 镜内那遍期间让 {@code Iris.getCurrentDimension()} 返回一个专用 id（{@code tacz:scope_pip}），
 * Iris 就为它单独建一套管线：独立 RenderTargets、独立程序、因而独立的那一整族 previous uniform
 * 实例。我们不自己持有它 —— 它躺在 Iris 的 map 里、由 Iris 管生死，切维度/重载包时
 * {@code destroyPipeline()} 遍历全 map 一并回收（自己 new 再塞进去就得自己管回收，
 * 漏一次就是显存泄漏，故 26.2 早否掉那条路）。</p>
 *
 * <p><b>全程反射</b>：本线 Iris 只在编译期可见（{@code modCompileOnly}）且我们刻意不 import 它的类，
 * 好让"结构变了"退化成安静放弃（退回与主画面共用管线 = 只差体验、不崩）。凡"静默失效"的
 * 风险都配了<b>回执</b>：{@link #noteShadowResolutionIntercepted()} 与 {@link #isShadowHookAlive()}
 * 把软注入失效变成一行明确告警，不靠人翻代码。</p>
 *
 * <h2>相对 26.1.2 的两处刻意裁剪</h2>
 * <ul>
 *   <li><b>Voxy 第二套渲染栈</b>（他们的 {@code VoxyScopePipelineCompat}）：本线没有
 *       {@code compat/voxy}，且缺它时 Voxy 的镜内行为未定义 ⇒ 我方在 {@link ScopePipRerender}
 *       里直接拒绝在装了 Voxy/Sodium 的情况下放行光影窄遍（见其 {@code shaderIsolateSafe()}）；</li>
 *   <li><b>Sodium 私有投影快照同步</b>：他们的窄遍要就地改写 Sodium 的
 *       {@code ProjectionMatrixBuffer} 私有快照，否则镜内地形留宽 FOV。本线 Sodium 非编译期可见，
 *       同样以「装了就不放行」替代。</li>
 * </ul>
 * 这两条的升级路径写在 {@code docs/lineage/SCOPE_PIP_SHADER_ISOLATION_PORT_2612_20260901.md}。
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

    /** 已经为哪一套主管线预热过。主管线换人（重载光影包/切维度）就要重来。 */
    private static Object prewarmedAgainst;
    private static boolean loggedPrewarm;
    /** 最近一次真构建时软注入是否拦到过 {@code getResolution()}（回执，见 {@link #noteShadowResolutionIntercepted()}）。 */
    private static boolean shadowHookLastBuild;

    /**
     * 是否正处在「瞄具那套 Iris 管线的构造过程」之中。只有这一小段窗口里
     * {@code PackShadowDirectives.getResolution()} 的返回值才决定<b>瞄具管线</b>的阴影图尺寸
     * （{@code IrisShadowResolutionMixin} 据此改小）；窗口之外必须原样放行，
     * 否则会把主画面的阴影一起改小。
     */
    private static volatile boolean buildingScopePipeline;

    /** 由 {@code IrisShadowResolutionMixin} 查询。 */
    public static boolean isBuildingScopePipeline() {
        return buildingScopePipeline;
    }

    /** 「释放失败过就不再重试」：释放不成功就让管线活着，别每帧折腾。 */
    private static boolean releaseFailed;

    /**
     * 当前活着的瞄具管线是按哪个 {@code ScopePipShadowScale} 建的；NaN = 还没建过。
     *
     * <p>阴影分辨率是管线<b>构造时</b>一次性读走定死的，此后 ShadowRenderTargets/ShadowRenderer
     * 全用这份快照 ⇒ 改了配置而旧管线还活着 = 改了等于没改。记下建时的值，逐帧比对，
     * 变了就销毁重建，让旋钮热生效。</p>
     */
    private static double appliedShadowScale = Double.NaN;

    /**
     * 构造窗口内是否真的拦到过 {@code getResolution()}。软注入是 {@code require = 0} 的，
     * Iris 内部挪个类它就静默失效、缩放旋钮变空转 —— 靠这个回执把「静默失效」写成一行告警。
     */
    private static volatile boolean shadowHookRanDuringBuild;

    public static void noteShadowResolutionIntercepted() {
        shadowHookRanDuringBuild = true;
    }

    /** 最近一次真构建里阴影钩子是否活着（供诊断与文档核对；未构建过时为 false）。 */
    public static boolean isShadowHookAlive() {
        return shadowHookLastBuild;
    }

    /**
     * {@code IrisCompat.isUsingRenderPack()} 是「每次调用一次 Class.forName」的反射查询，
     * 而预热挂在 render HEAD、逐帧都走 —— 所以这里按 20 帧节流缓存一份。
     *
     * <p>滞后的两个方向都无害：刚装包最多晚 20 帧开始预热（只是多卡一次首镜）；刚关包后
     * 预热还会多跑 ≤20 帧，那时 Iris 的管线管理器已空，走到 {@code manager == null} 就返回。</p>
     */
    private static boolean packInUseCached;
    private static int packInUseCountdown;

    public static boolean shaderPackActiveCached() {
        if (packInUseCountdown-- <= 0) {
            packInUseCountdown = 19;
            packInUseCached = IrisCompat.isUsingRenderPack();
        }
        return packInUseCached;
    }

    private static double wantedShadowScale() {
        return RenderConfig.SCOPE_PIP_SHADOW_SCALE == null
                ? 1.0d : RenderConfig.SCOPE_PIP_SHADOW_SCALE.get();
    }

    private IrisScopePipelineCompat() {
    }

    /**
     * 时域隔离开关（{@code ScopePipIsolatePipeline}）。配置未加载时按<b>开启</b>处理，
     * 与其它 ScopePip 配置的 null 兜底方向一致。
     */
    public static boolean isolatePipelineEnabled() {
        return RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE == null
                || RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE.get();
    }

    /** 反射句柄是否可用（不可用时隔离整体退化为「共用主管线」，由调用方决定是否硬拒）。 */
    public static boolean handlesAvailable() {
        return resolveHandles();
    }

    /**
     * 瞄具那套管线用的维度 id。
     *
     * @return Iris 的 {@code NamespacedId} 实例；拿不到时 {@code null}（此时不做隔离）
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
            // PipelineManager 里只有一个 Map 字段（pipelinesPerDimension）：按类型找，
            // 字段名变了也不至于抓空。
            for (Field field : manager.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    pipelinesMapField = field;
                    break;
                }
            }
            if (pipelinesMapField != null) {
                pipelinesMapField.setAccessible(true);
            }
            GunMod.LOGGER.info("[TACZ Scope] Iris pipeline-manager handles resolved (map field: {}). "
                            + "Scope-pass isolation {}",
                    pipelinesMapField == null ? "none" : "ok",
                    pipelinesMapField == null ? "will fall back to the shared pipeline" : "is available");
        } catch (Throwable t) {
            handlesFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Could not resolve Iris' pipeline manager; the scope "
                    + "pipeline will be built lazily on first aim (expect one stutter).", t);
        }
        return !handlesFailed;
    }

    /**
     * 若存在就整份销毁瞄具管线，归还它占用的<b>全部</b> GPU 资源（colortex/gbuffer/阴影图/SSBO/程序，
     * 见 {@code IrisRenderingPipeline#destroy}）。
     *
     * <p>两个用途：①{@code ScopePipShadowScale} 改动后的热重建（旧的不死、新值读不到）；
     * ②空闲释放（{@code ScopePipReleaseIdlePipeline}）—— 26.1.2 查明「光影下开镜帧率自首次
     * ADS 起持续衰减、重进存档重置」强烈指向每遍 scope pass 在瞄具管线的保留 GPU 状态里累积，
     * 空闲时整份销毁、下次开镜由 {@link #prewarmIfNeeded()} 重建即可清零。</p>
     *
     * <p>只在「不在镜内那一遍、且处于帧内世界渲染之前的安全位置」调用。销毁后顺手失效预热状态。</p>
     *
     * @return 真的销毁了才 true
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
            // 兜底闸：正被镜内那一遍用着就绝不销毁（销毁正在绑定的管线 = 把主画面推向已销毁的
            // RenderTargets；26.1.2 的 ESC 崩溃教训是「释放必须发生在窄遍之外」）。
            if (ScopePipRerender.isInsideScopeLevelRender()) {
                GunMod.LOGGER.warn("[TACZ Scope] Refusing to release the scope pipeline while the scope "
                        + "pass is running; retried next idle window.");
                return false;
            }
            scope.getClass().getMethod("destroy").invoke(scope);
            pipelines.remove(id);
            // PipelineManager.pipeline 若正指着刚销毁的那套，指回主管线，
            // 别让后续任何 getPipelineNullable() 的消费者拿到已释放的管线。
            Object current = getPipelineNullable.invoke(manager);
            if (current == scope) {
                Object real = getCurrentDimension.invoke(null);
                if (real != null) {
                    preparePipeline.invoke(manager, real);
                }
            }
            prewarmedAgainst = null;
            appliedShadowScale = Double.NaN;
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
     * 还没预热过就在<b>本帧的安全位置</b>把瞄具管线建好。
     *
     * <p>由 {@code GameRendererMixin} 的 render HEAD 调用 —— 世界渲染之前、不在任何 render pass 内、
     * 也不在镜内那一遍里（与 26.1.2 的「帧内世界渲染前的空档」同类）。首次开镜不再卡在编译
     * shaderpack 上。</p>
     */
    public static void prewarmIfNeeded() {
        if (!ScopePipRerender.rerenderMode() || !isolatePipelineEnabled()) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("iris") || !shaderPackActiveCached()) {
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
                // 主管线还没建起来（刚进世界）。抢在它前面预热会让「当前管线」指向瞄具那套，
                // 把这一帧的主画面画错 —— 等它先建好，下一帧再说。
                return;
            }
            // 【热生效】阴影分辨率构造时定死：配置变了而旧管线还活着就先销毁它，让下面的重建
            // 按新值走。必须放在快速路径<b>之前</b>，否则「已就绪」直接短路，改配置永远等到下次进世界。
            if (prewarmedAgainst == mainPipeline
                    && !Double.isNaN(appliedShadowScale)
                    && Math.abs(appliedShadowScale - wantedShadowScale()) > 1.0e-3) {
                GunMod.LOGGER.info("[TACZ Scope] ScopePipShadowScale changed ({} -> {}); rebuilding the "
                                + "scope pipeline so the new shadow map size takes effect.",
                        appliedShadowScale, wantedShadowScale());
                if (!releaseScopePipelineIfPresent()) {
                    // 释放失败/被熔断：把「已应用值」改记为目标值停止重试（否则本分支每帧都进、
                    // 日志刷屏）。旧管线继续用旧尺寸，等下次自然重建（切维度/重载包）再生效。
                    appliedShadowScale = wantedShadowScale();
                }
            }
            if (prewarmedAgainst == mainPipeline) {
                return; // 稳态快速路径：本方法逐帧被调，就绪时不碰任何反射
            }
            Object realDimension = getCurrentDimension.invoke(null);
            // 「这次是真构建还是缓存命中」决定下面的钩子核验有没有意义：
            // 缓存命中不会读 getResolution()，拿它去核验只会误报。
            boolean wasAbsent = pipelinesMapField != null
                    && !((Map<?, ?>) pipelinesMapField.get(manager)).containsKey(id);
            buildingScopePipeline = true;
            shadowHookRanDuringBuild = false;
            try {
                // 这个窗口同时也是「瞄具管线正在构造」的唯一时机：阴影图分辨率就在构造里被读走，
                // 且此后由采样器一路捕获使用（要给镜内配小阴影图只有这里来得及）。
                preparePipeline.invoke(manager, id);
            } finally {
                buildingScopePipeline = false;
            }
            if (wasAbsent) {
                appliedShadowScale = wantedShadowScale();
                shadowHookLastBuild = shadowHookRanDuringBuild;
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

    /** 诊断用：瞄具管线当前是否已在 Iris 的 map 里（不触发构建）。 */
    public static boolean isScopePipelineBuilt() {
        Object id = scopeDimensionId();
        if (id == null || !resolveHandles() || pipelinesMapField == null) {
            return false;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            if (manager == null) {
                return false;
            }
            return ((Map<?, ?>) pipelinesMapField.get(manager)).containsKey(id);
        } catch (Throwable t) {
            return false;
        }
    }
}
