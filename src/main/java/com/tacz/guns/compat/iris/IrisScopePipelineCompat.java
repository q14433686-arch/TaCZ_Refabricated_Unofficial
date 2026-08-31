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
            // 【稳态快速路径】本方法逐帧都会被调到，「已就绪」必须最便宜。
            if (prewarmedAgainst == mainPipeline) {
                return;
            }
            Object realDimension = getCurrentDimension.invoke(null);
            // 打开窗口：把瞄具那套设成「当前管线」。第一次会真的编译，之后是缓存命中。
            preparePipeline.invoke(manager, id);
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
