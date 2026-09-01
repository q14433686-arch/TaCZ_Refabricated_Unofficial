package com.tacz.guns.compat.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 瞄具专用 Iris 管线的「维度 id」与<b>预热</b>。
 *
 * <h2>为什么要预热，而不是等第一次开镜时懒加载</h2>
 * Iris 的管线是用到才建的。不预热的话，那套瞄具管线会在<b>第一次开镜的那一帧</b>
 * 才开始编译整个 shaderpack —— 玩家实测「第一次 ADS 卡得非常厉害」就是它。
 * 而且那一刻正好落在我们的镜内那一遍<b>里面</b>，等于在一帧的中途做重活：
 * <ul>
 *   <li>{@code preparePipeline} 建管线时会调
 *       {@code SystemTimeUniforms.COUNTER.reset()} 与 {@code TIMER.reset()}，
 *       把<b>全局</b>帧计数与计时器清零 —— 在一帧中途干这个，时域效果当场错乱；</li>
 *   <li>Voxy 挂在「每个 Iris 管线构造」上的那套钩子也在此刻触发，
 *       于是它的资源构建发生在一个极不合适的时机。</li>
 * </ul>
 * 把它挪到进世界后的<b>普通帧</b>去做，这两件事就都发生在安全的位置，
 * 卡顿也从「战斗中第一次举镜」变成「进世界后一次性」。
 *
 * <h3>预热之后要把「当前管线」指回去</h3>
 * {@code preparePipeline} 除了建/取，还会把 {@code PipelineManager.pipeline}
 * 指向刚取到的那套。预热完必须再用<b>真实维度</b>调一次把它指回主管线 ——
 * 那次是命中缓存，不会重建。漏掉这一步，接下来整帧都会用瞄具管线渲染主画面。
 *
 * <p>全程反射，Iris 不在或结构变了就静默放弃（退回懒加载，只是第一次开镜会卡）。
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
    /** Voxy 那一套是否已经尘埃落定（建好了，或确定用不上）。稳态快速路径就看它。 */
    private static boolean voxyStackSettled;
    /**
     * 是否正处在「瞄具那套 Iris 管线的构造过程」之中。
     *
     * <p>只有这一小段窗口里，{@code PackShadowDirectives.getResolution()} 的返回值
     * 才会决定<b>瞄具管线</b>那张阴影贴图的尺寸。窗口之外必须恢复原值，
     * 否则会把主画面的阴影一起改小。
     */
    private static volatile boolean buildingScopePipeline;

    /** 供 {@code IrisShadowResolutionMixin} 查询。 */
    public static boolean isBuildingScopePipeline() {
        return buildingScopePipeline;
    }

    /**
     * {@code LevelExtractor.allChanged()} 真的执行了 —— 也就是 Voxy 刚把整个
     * {@code VoxyRenderSystem} 拆了重建（改区块视距、F3+A、切资源包都会走到这里）。
     *
     * <p>光靠 {@link #prewarmIfNeeded()} 是发现不了的：它的稳态快速路径盯的是
     * <b>Iris 主管线</b>有没有换人，而 {@code allChanged()} 根本不碰 Iris 管线，
     * 于是 {@code voxyStackSettled} 一直是 true，我们逐帧直接返回，
     * 永远不会去问一句「Voxy 还是原来那个吗」。所以必须由这条事件来打破快速路径。
     */
    public static void onLevelRendererReload() {
        // 顺序：先把已经失效的那一套还回去，再把状态机打回「需要重新检查」。
        com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.onRendererRebuilt();
        voxyStackSettled = false;
    }
    private static boolean loggedPrewarm;

    private IrisScopePipelineCompat() {
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

    /** 瞄具那套管线的实例；还没建（或拿不到）时返回 null。仅供诊断/实验读取。 */
    public static Object scopePipeline() {
        if (!resolveHandles() || pipelinesMapField == null) {
            return null;
        }
        Object id = scopeDimensionId();
        if (id == null) {
            return null;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            if (manager == null) {
                return null;
            }
            Map<?, ?> pipelines = (Map<?, ?>) pipelinesMapField.get(manager);
            return pipelines.get(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 主管线实例；仅供诊断/实验读取。 */
    public static Object mainPipeline() {
        if (!resolveHandles()) {
            return null;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            return manager == null ? null : getPipelineNullable.invoke(manager);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 当前管线表里有多少套管线（诊断：正常应恒为 1 或 2）。 */
    public static int pipelineMapSize() {
        if (!resolveHandles() || pipelinesMapField == null) {
            return -1;
        }
        try {
            Object manager = getPipelineManager.invoke(null);
            if (manager == null) {
                return -1;
            }
            return ((Map<?, ?>) pipelinesMapField.get(manager)).size();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 「释放失败过就不再重试」的一次性标志：释放不成功就保持管线活着，别每帧折腾。 */
    private static boolean releaseFailed;

    /**
     * 当前活着的瞄具管线是按哪个 ScopePipShadowScale 建的；NaN = 还没建过。
     *
     * <p>阴影贴图分辨率是管线<b>构造时</b>读 {@code PackShadowDirectives.getResolution()}
     * 一次性定死的（构造器里 {@code shadowMapResolution = getResolution()} 捕获成字段，
     * 此后 ShadowRenderTargets / ShadowRenderer 全用这份快照）。所以配置改了而管线
     * 还活着 = 改了等于没改 —— 用户实测「貌似不生效」的最可能成因。记下建管线时的值，
     * {@link #prewarmIfNeeded()} 每帧比对，变了就销毁重建，让旋钮热生效。</p>
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
     * 【光影下开镜帧率持续衰减 · 实验开关的配套】空闲时销毁瞄具那套 Iris 管线，
     * 释放它占用的<b>全部</b> GPU 资源（colortex/gbuffer/阴影图/SSBO/程序，见
     * {@code IrisRenderingPipeline#destroy} 逐项实读）。
     *
     * <p>背景：用户报告「距第一次开镜的时间越长、开镜帧数越低；与是否持续开镜无关；
     * 重进存档重置；仅 {@code ScopePipIsolatePipeline=true}」。姊妹分支的 CPU 侧结构
     * 探针（管线身份、map 大小、SSBO 数、Blaze3D 保留集合）全部无果 —— 这强烈指向
     * <b>每 scope pass 在瞄具管线的保留 GPU 状态里累积</b>（CPU 侧看不见）。本方法把
     * 那套管线整份销毁，下一帧开镜时由 {@link #prewarmIfNeeded()} 重建：
     * <ul>
     *   <li>若开启空闲释放后衰减消失 ⇒ 累积源确实在瞄具管线的保留资源里；</li>
     *   <li>若衰减依旧 ⇒ 累积源在主管线 / 驱动层 / shaderpack 的进程级状态里。</li>
     * </ul>
     *
     * <p>只在「不在镜内那一遍、且处于 extract 的安全位置」调用（见 ScopePipRenderer）。
     * 销毁后顺手失效预热状态与 Voxy 第二套栈（它绑的就是这套管线）。</p>
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
            Map<?, ?> pipelines = (Map<?, ?>) pipelinesMapField.get(manager);
            Object scope = pipelines.get(id);
            if (scope == null) {
                return false;
            }
            // 【保险带 · 05170 d3f0fdc 移植】瞄具管线上若绑着一个不是我们第二套栈
            // 的 Voxy 管线（= Voxy 主栈在某个漏网的重建窗口绑了上来），此刻销毁
            // 它会让主画面下一帧在 Voxy 里崩 "Tried to use destroyed RenderTargets"。
            // 拒绝释放并对本会话熔断 —— 防的是任何我们还没发现的改绑路径。
            if (com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.isForeignVoxyBoundTo(scope)) {
                releaseFailed = true;
                GunMod.LOGGER.warn("[TACZ Scope] Refusing to release the idle scope pipeline: Voxy's MAIN "
                        + "render stack is bound to it (a rebind slipped past the reload gates). Destroying "
                        + "it would crash the main view. Idle release is disabled for this session; "
                        + "please report this log line.");
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
            // 预热状态与 Voxy 第二套栈都随这套管线一起失效。
            prewarmedAgainst = null;
            voxyStackSettled = false;
            appliedShadowScale = Double.NaN;
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
     * <p>由 {@code GameRenderer#extract} 的 HEAD 调用 —— 那里在世界渲染<b>之前</b>，
     * 不在任何 render pass 内，也不在我们的镜内那一遍里。
     *
     * <p>调用方负责判断「现在确实需要隔离管线」，本方法只管建与不建。
     */
    public static void prewarmIfNeeded() {
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
            // 【ScopePipShadowScale 热生效】阴影分辨率是管线构造时一次性定死的
            // （见 appliedShadowScale 的注释）——配置变了而旧管线还活着，就先销毁它，
            // 让下面的重建按新值走。放在快速路径之前：否则「已就绪」直接短路，
            // 改配置永远等到下次进世界才生效。
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
            //
            // 早前这里每帧都要 VoxyCompat.renderSystem() + isBuiltFor()，
            // 明明什么都不用做，却照样付两次反射调用。
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
            // 就是在构造里读 PackShadowDirectives.getResolution() 定下来的，
            // 且此后由采样器一路捕获使用。所以要给镜内那一遍配一张更小的阴影图，
            // 只有在这里做才来得及。见 IrisShadowResolutionMixin。
            buildingScopePipeline = true;
            shadowHookRanDuringBuild = false;
            try {
                preparePipeline.invoke(manager, id);
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
                // 【只能在这个窗口里建】Voxy 的 RenderPipelineFactory 取的正是
                // 「当前管线」，错过这里就会绑到主管线上，等于白建。
                if (needVoxyStack) {
                    com.tacz.guns.compat.voxy.VoxyScopePipelineCompat.ensureBuilt(voxy);
                }
            } catch (Throwable ignored) {
                // Voxy 那侧失败只影响镜内有没有 LOD，不该拖累管线预热本身
            } finally {
                // 【必须】把当前管线指回主管线，否则这一帧的主画面会用瞄具那套渲染。
                // 放 finally：上面抛了也绝不能把「当前管线」留在瞄具那套上。
                if (realDimension != null) {
                    preparePipeline.invoke(manager, realDimension);
                }
            }
            prewarmedAgainst = mainPipeline;
            if (!loggedPrewarm) {
                loggedPrewarm = true;
                GunMod.LOGGER.info("[TACZ Scope] Pre-built the scope pass' Iris pipeline now, so the "
                        + "first time you aim does not stall while the shader pack compiles.");
            }
        } catch (Throwable t) {
            handlesFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Failed to pre-build the scope pipeline; it will be built "
                    + "on first aim instead (expect one stutter).", t);
        }
    }
}
