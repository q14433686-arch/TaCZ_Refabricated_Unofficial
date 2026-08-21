package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 【诊断】把「镜内二次渲染」那一帧的渲染目标解析过程原样打出来。
 *
 * <h2>为什么需要它</h2>
 * 「放大画面溢出到镜外」这个症状，靠静态分析已经连错四次：
 * 先怪 Sodium 绕开 {@code mainRenderTarget()}（错，它走），
 * 再怪 ImmediatelyFast 缓存帧缓冲（错，它只是跳过绑 0），
 * 再怪 SkyRenderer 缓存 target（是真 bug，但会被 vanilla 的 clear 抹掉，解释不了溢出），
 * 再怪 Voxy 是第三个地形渲染器（错，它的 target 与投影都取自被我们接管的两处）。
 *
 * <p>共同的失败模式是：<b>症状对得上就当成因果</b>。
 * 而这个问题的关键事实其实只有两条，都可以直接测出来，不必猜：
 * <ol>
 *   <li>镜内那一遍进行期间，有没有<b>谁解析到了真正的主 target</b>（= 漏出去的那一笔）；</li>
 *   <li>vanilla 的 clear 之后，还有没有人拿着我们的窄投影在画
 *       （= 延迟提交，重定向再对也没用）。</li>
 * </ol>
 *
 * <h2>怎么测</h2>
 * {@code GameRenderer#mainRenderTarget()} 的注入点本来就是<b>所有</b>目标解析的必经之路
 * （Sodium 的 {@code TerrainRenderPass}、Voxy 经由它、vanilla 的 clear lambda、帧图导入……
 * 全都从这里过）。在那里记一行「谁问的 + 给了哪个」，再配上镜内那一遍的进出标记，
 * 一帧的完整顺序就出来了。
 *
 * <p>只在 {@code ScopePipDebugTrace} 打开时工作，且<b>只记录有限几帧</b> ——
 * 这条路径每帧被调用几十次，不封顶会瞬间把日志淹掉。
 */
@Environment(EnvType.CLIENT)
public final class ScopePipTrace {

    /** 最多记录几帧。够看清一帧的完整顺序，又不会把日志刷爆。 */
    private static final int FRAME_BUDGET = 3;
    /** 单帧内最多记录多少行，防御性上限（正常一帧几十行）。 */
    private static final int LINES_PER_FRAME = 400;

    /**
     * 【硬性兜底】最多<b>武装</b>这么多帧，之后无论有没有采到样本都永久收摊。
     *
     * <h3>为什么必须有这一条 —— 它修的是一次真实的崩溃</h3>
     * 原来的收摊条件只有 {@link #FRAME_BUDGET}，而预算<b>只在
     * {@code sawScopePass} 为真的帧才扣</b>，那个标志又只由
     * {@code renderScopeView}（二次渲染那条路）发出的 {@code SCOPE-PASS BEGIN} 置位。
     * <b>光影下二次渲染是被硬性拦下的</b>，于是那面旗永远立不起来 ⇒ 预算一分不扣 ⇒
     * {@code enabled()} 永远为真 ⇒ 整局游戏每帧都在 {@code mainRenderTarget()} 上跑
     * {@code StackWalker}（每帧最多 400 次，而 Sodium 地形、Voxy、帧图都会调它）。
     *
     * <p>后果不是「日志有点多」，而是<b>渲染线程被拖垮</b>：区块构建在工作线程照常堆积，
     * 渲染线程追不上，{@code processChunkBuildResults} 只好一次性上传巨量结果，
     * Sodium 的 {@code GlBufferArena.resize} 于是要一口气申请 88 MB
     * （且扩容期间新旧缓冲并存 ≈ 2 倍），显存直接 {@code GpuOutOfMemoryException}。
     * 实测：只在<b>四处跑图</b>时触发（那才有大量区块构建），静止测试区完全正常 ——
     * 这也是它一开始被误判成「显存泄漏」的原因。
     *
     * <p>教训：诊断开关的收摊条件<b>绝不能依赖被诊断的那条路自己发信号</b> ——
     * 那条路不跑，正是你要诊断的情形。所以这里补一个与任何业务逻辑无关的绝对上限。
     */
    private static final int ARMED_FRAME_LIMIT = 600;

    private static int framesTraced = 0;
    private static int framesArmed = 0;
    private static int linesThisFrame = 0;
    private static boolean tracingThisFrame = false;
    private static boolean announced = false;
    private static boolean loggedGiveUp = false;

    /**
     * 本帧的行缓冲。
     *
     * <h3>为什么要缓冲，而不是边走边打</h3>
     * 第一版直接打，结果三帧预算<b>在标题画面上就烧光了</b> ——
     * 那几帧里解析 target 的是 {@code CubeMap#render} 和 {@code GuiRenderer#render}，
     * 与瞄具毫无关系，等玩家真进世界举枪时已经没有预算了，日志里一片空白。
     *
     * <p>改成先缓冲、<b>只有当这一帧确实跑了镜内那一遍</b>才落盘并计入预算。
     * 于是三帧预算一定花在有意义的帧上。
     */
    private static final List<String> BUFFER = new ArrayList<>();
    /** 本帧是否真的跑了镜内那一遍（缓冲要不要落盘就看它）。 */
    private static boolean sawScopePass = false;

    private ScopePipTrace() {
    }

    public static boolean enabled() {
        return RenderConfig.SCOPE_PIP_DEBUG_TRACE != null
                && RenderConfig.SCOPE_PIP_DEBUG_TRACE.get()
                && framesTraced < FRAME_BUDGET
                // 与业务逻辑无关的绝对上限，见 ARMED_FRAME_LIMIT ——
                // 没有它，采不到样本的场景下本开关会一直武装到游戏崩溃。
                && framesArmed < ARMED_FRAME_LIMIT;
    }

    /**
     * 每帧开头调用（接在瞄具帧状态归零那一处）：结算上一帧，再开一帧的缓冲。
     *
     * <p>结算规则就是本类存在的理由 —— <b>只有跑过镜内那一遍的帧才落盘、才计入预算</b>。
     * 没跑的帧（标题画面、没举枪、没开镜）整份丢弃，一行都不打。
     */
    public static void beginFrame() {
        if (tracingThisFrame && sawScopePass && !BUFFER.isEmpty()) {
            framesTraced++;
            GunMod.LOGGER.info("[TACZ Scope][trace] ---- frame with scope pass ({}/{}) ----",
                    framesTraced, FRAME_BUDGET);
            for (String line : BUFFER) {
                GunMod.LOGGER.info("[TACZ Scope][trace] {}", line);
            }
            GunMod.LOGGER.info("[TACZ Scope][trace] ---- end frame ----");
        }
        BUFFER.clear();
        sawScopePass = false;
        linesThisFrame = 0;
        tracingThisFrame = enabled();
        if (tracingThisFrame) {
            // 无条件计数：这一帧确实处于武装状态、确实会跑 StackWalker，就得算数。
            // 绝不能只在「采到样本」时才计 —— 那正是原来那个 bug。
            framesArmed++;
        } else if (RenderConfig.SCOPE_PIP_DEBUG_TRACE != null
                && RenderConfig.SCOPE_PIP_DEBUG_TRACE.get()
                && framesArmed >= ARMED_FRAME_LIMIT && !loggedGiveUp) {
            loggedGiveUp = true;
            GunMod.LOGGER.info("[TACZ Scope][trace] Gave up after {} armed frames without capturing a "
                            + "scope pass; tracing is now off for this session. Leaving it armed would "
                            + "stall the render thread badly enough to starve terrain uploads. "
                            + "Set ScopePipDebugTrace=false to silence this.", framesArmed);
        }
        if (tracingThisFrame && !announced) {
            announced = true;
            GunMod.LOGGER.info("[TACZ Scope][trace] Armed: will capture {} frames that actually run the "
                    + "scope pass. Any 'MAIN' between SCOPE-PASS BEGIN and END is the leak; its stack "
                    + "trace names the culprit.", FRAME_BUDGET);
        }
    }

    /** 记一个阶段标记（镜内那一遍的进出、合成等）。 */
    public static void mark(String what) {
        if (!tracingThisFrame) {
            return;
        }
        if (what.startsWith("SCOPE-PASS BEGIN") || what.startsWith("PIP COMPOSITE")) {
            // 这一帧值得记 —— 结算时才会落盘。
            //
            // 必须同时认「PIP COMPOSITE」：光影下走的是屏幕空间合成，
            // 根本不存在 SCOPE-PASS。只认前者的话，光影下永远采不到样本、
            // 预算永远扣不掉 —— 那正是拖垮渲染线程、进而撑爆显存的那个 bug。
            sawScopePass = true;
        }
        if (linesThisFrame++ > LINES_PER_FRAME) {
            return;
        }
        BUFFER.add("== " + what);
    }

    /**
     * 记一次 {@code mainRenderTarget()} 解析。
     *
     * @param resolved   实际返回的 target
     * @param redirected 是否被我们顶替成了离屏 target
     */
    public static void targetResolved(@Nullable RenderTarget resolved, boolean redirected) {
        if (!tracingThisFrame || linesThisFrame++ > LINES_PER_FRAME) {
            return;
        }
        BUFFER.add((redirected ? "SCOPE <- " : "MAIN  <- ") + caller());
    }

    /**
     * 谁问的。跳过 JDK / 本类 / mixin 胶水，取最上面几层真正的调用者。
     *
     * <p>用 {@code StackWalker} 而不是 {@code new Throwable().getStackTrace()}：
     * 前者可以只取需要的深度，代价小得多 —— 虽然这是调试路径，
     * 但它每帧要跑几十次，太慢会把时序本身也带偏，那就测不准了。
     */
    private static String caller() {
        try {
            List<String> frames = StackWalker.getInstance().walk(s -> s
                    .map(f -> f.getClassName() + "#" + f.getMethodName())
                    .filter(n -> !n.startsWith("com.tacz.guns.client.render.scope.ScopePipTrace"))
                    .filter(n -> !n.contains("GameRendererMixin"))
                    .skip(1)
                    .limit(3)
                    .collect(Collectors.toList()));
            return String.join("  <-  ", frames);
        } catch (Throwable t) {
            return "<stack unavailable>";
        }
    }
}
