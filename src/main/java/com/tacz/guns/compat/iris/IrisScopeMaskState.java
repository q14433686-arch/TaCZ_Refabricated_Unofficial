package com.tacz.guns.compat.iris;

import javax.annotation.Nullable;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import com.tacz.guns.config.client.RenderConfig;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime bridge for the Iris HAND shader scope-mask integration.
 *
 * <p>This class manages the per-draw uniform state for patched Iris shaders so that
 * custom scope clipping runs accurately when scope body or reticle passes are submitted,
 * while all standard passes (gun body, attachments, player hands, entities) are explicitly
 * set to {@code mode = 0} on every draw call to prevent uniform leakage and random clipping.</p>
 */
public final class IrisScopeMaskState {
    private static final String BODY_PIPELINE = "pipeline/scope_body_clipped";
    private static final String FLASH_TRANSLUCENT_PIPELINE = "pipeline/scope_flash_translucent_clipped";
    private static final String FLASH_SWIRL_PIPELINE = "pipeline/scope_flash_swirl_clipped";
    private static final String RETICLE_PIPELINE = "pipeline/scope_reticle_clipped";
    private static final String RETICLE_EMISSIVE_PIPELINE = "pipeline/scope_reticle_emissive_clipped";
    private static final String TEXT_PIPELINE = "pipeline/scope_text_clipped";
    private static final String MASK_SAMPLER = "ScopeMaskSampler";
    private static final String UNIFORM_MODE = "tacz_ScopeMaskMode";
    private static final String UNIFORM_SAMPLER = "tacz_ScopeMaskSampler";

    private static boolean loggedFailure;
    private static boolean loggedApply;
    private static boolean loggedProgramMismatch;

    // ───────────────────────── 光影链路探针 ─────────────────────────
    // 光影下裁剪失效时，故障可能停在链路上任意一环，而其中多数环节原本是
    // 「静默返回」——日志里一个字都没有，只能靠现象反推（已经反推了两轮）。
    // 这组计数器把每一环的实际走向记下来，由 logProbeOnce() 在首次开镜后
    // 汇总成一行，让下一份日志直接指出断点。
    // 全部是普通 int/boolean，只在 Render 线程写，不加锁；开销可忽略。
    /** applyToShaderProgram 被调用的次数（=IrisExtendedShaderMixin 装上了没有）。 */
    private static int probeShaderSetupCalls;
    /** applyToGlRenderPass 被调用的次数（=IrisGlCommandEncoderMixin 装上了没有）。 */
    private static int probeRenderPassCalls;
    /** resolveMode 返回非 0 的次数（=管线 location 认出来了没有）。 */
    private static int probeNonZeroMode;
    /** 因程序里找不到 tacz_ScopeMaskMode 而放弃的次数（=着色器注入成功没有）。 */
    private static int probeNoModeUniform;
    /** 因拿不到掩码纹理而把 mode 强写回 0 的次数。 */
    private static int probeNoMaskTexture;
    /** 真正把 mode!=0 写进程序的次数（=裁剪到底有没有生效）。 */
    private static int probeModeWritten;
    /** IrisShaderCreatorMixin 成功注入 tacz 分支的 HAND 程序数（由它上报）。 */
    private static int probeHandProgramsPatched;
    /**
     * 「后端管线对象 → 管线 location 路径」登记表，由
     * {@code FrontendRenderPassPipelineMixin} 在 {@code FrontendRenderPass#setPipeline}
     * 处填入。
     *
     * <p>26.3 后端管线已经查不回前端 location（{@code GlRenderPipeline#info()} 被删），
     * 而光影激活时后端的 {@code getDebugLabel()} 拿到的是 Iris 自己的程序名
     * （实机探针：{@code sky_basic}），不是我们的 location。唯一还同时握着
     * 「名字」和「后端对象」的地方就是前端那次交接，所以在那里配对记下来。</p>
     *
     * <p>用弱键 map：键是后端管线对象，管线销毁（切光影包/重载资源）后该条目
     * 自动可回收，不会把已 close 的管线钉在内存里。容量很小（全局管线数量级），
     * 但仍设上限兜底，超了就整表清空重来 —— 与 MODE_BY_PIPELINE 同一策略。</p>
     */
    private static final java.util.Map<Object, String> NAME_BY_BACKEND_PIPELINE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final int NAME_CACHE_LIMIT = 512;

    /**
     * 由 {@code FrontendRenderPassPipelineMixin} 调用：登记这条前端管线的名字与它的后端对象。
     *
     * @param frontendPipeline {@code FrontendRenderPipeline}（record，有 name() 与
     *                         backendRenderPipeline() 两个组件）
     */
    public static void notePipelineBinding(Object frontendPipeline) {
        if (frontendPipeline == null) {
            return;
        }
        // 【无光影时整条路必须零成本 —— 2026-09-19 用户实测：关了光影开镜表现也不对】
        //
        // IrisCompatMixinPlugin#shouldApplyMixin 的判据只有 isModLoaded("iris")，
        // 【不看光影包是否启用】。装了 Iris 但关着光影的玩家（正是这位用户的情形），
        // FrontendRenderPassPipelineMixin 照样被装上，于是本方法在
        // FrontendRenderPass#setPipeline 上【每个绘制批次】都被调一次 ——
        // 本方法自己的注释就写着「实机一次开镜就有数万次」。
        //
        // 而这张表存在的唯一理由，是绕开「光影激活时 Iris 把 pack 程序换进管线、
        // 后端 name()/debugLabel 都变成 Iris 程序名」这个障碍。光影没开就
        // 根本没有这个障碍：getDebugLabel() 此时返回的就是我们的 location
        // （PipelineBuilder:346 → GlPipelineRecompiler:314），回退路径本来就够用。
        // 所以无光影时登记纯属白干活，还会把下面那次 map 查询压到热路径上。
        //
        // isUsingRenderPack() 每帧只算一次、之后读一个 byte 字段，
        // 放在最前面即可把整条路的成本降到一次布尔判断。
        if (!IrisCompat.isUsingRenderPack()) {
            return;
        }
        // 【热路径】setPipeline 每个绘制批次都会调到（与 renderPass 计数同数量级，
        // 实机一次开镜就有数万次）。所以这里做两件事把成本压到一次 map 查询：
        //   1. SEEN_FRONTEND_PIPELINES 记住「这个前端管线对象已经处理过」，
        //      管线对象在一局内是复用的，真正需要反射的只有头几次；
        //   2. name()/backendRenderPipeline() 两个 Method 对象按 class 缓存，
        //      避免 getMethod 每次返回防御性拷贝（invokeNoArgs 的固有开销）。
        if (tacz$markSeen(frontendPipeline)) {
            return;
        }
        try {
            Class<?> cls = frontendPipeline.getClass();
            if (cls != cachedFrontendClass) {
                cachedFrontendNameMethod = cls.getMethod("name");
                cachedFrontendBackendMethod = cls.getMethod("backendRenderPipeline");
                cachedFrontendNameMethod.setAccessible(true);
                cachedFrontendBackendMethod.setAccessible(true);
                cachedFrontendClass = cls;
            }
            Object name = cachedFrontendNameMethod.invoke(frontendPipeline);
            Object backend = cachedFrontendBackendMethod.invoke(frontendPipeline);
            if (name == null || backend == null) {
                return;
            }
            String path = stripModNamespace(String.valueOf(name));
            if (path == null) {
                // 不是本 mod 的管线：不记，省得把表撑大。
                return;
            }
            if (NAME_BY_BACKEND_PIPELINE.size() >= NAME_CACHE_LIMIT) {
                NAME_BY_BACKEND_PIPELINE.clear();
            }
            NAME_BY_BACKEND_PIPELINE.put(backend, path);
            if (probeFirstTaczPath == null) {
                probeFirstTaczPath = path;
            }
        } catch (Throwable t) {
            logOnce("record frontend pipeline binding", t);
        }
    }

    /**
     * 已处理过的前端管线对象。见 {@link #notePipelineBinding}。
     *
     * <p>【2026-09-19 改】原先是 {@code Collections.synchronizedMap(new WeakHashMap<>())}，
     * 而本方法跑在<b>每个绘制批次</b>上。那个组合在热路径上有三笔固定开销：
     * 一次全局锁、{@code WeakHashMap} 每次 get/put 都要跑
     * {@code expungeStaleEntries()} 清引用队列、以及
     * {@code FrontendRenderPipeline} 作为 record 的<b>按值</b> hashCode
     * （要对各组件逐个求哈希）。三者叠起来就是「装了 Iris、关着光影，
     * 开镜表现也不对」的那一份 —— 而那时这张表根本用不上。</p>
     *
     * <p>现在：无光影时调用方直接早退（见上），光影下也换成不加锁的普通
     * {@code HashMap} —— 本类只在 Render 线程被触碰（{@code setPipeline} 与
     * {@code setupDraw} 都在 Render 线程），不需要锁；键改用记录本身的
     * 按值相等（HashMap 语义不变），但少了弱引用队列与同步开销。
     * 条目数量级是「全局管线数」（vanilla 102 条 + Iris 若干），仍设上限兜底。</p>
     */
    private static final java.util.Map<Object, Boolean> SEEN_FRONTEND_PIPELINES =
            new java.util.HashMap<>();

    private static final int SEEN_CACHE_LIMIT = 4096;

    /** @return true 表示这个前端管线对象此前已经处理过，调用方应直接返回。 */
    private static boolean tacz$markSeen(Object frontendPipeline) {
        if (SEEN_FRONTEND_PIPELINES.containsKey(frontendPipeline)) {
            return true;
        }
        if (SEEN_FRONTEND_PIPELINES.size() >= SEEN_CACHE_LIMIT) {
            SEEN_FRONTEND_PIPELINES.clear();
        }
        SEEN_FRONTEND_PIPELINES.put(frontendPipeline, Boolean.TRUE);
        return false;
    }

    /** {@link #noteCompiledBinding} 被调用的次数（每次同步 = 每条管线一次）。 */
    private static int probeBindingSyncAttempts;
    /** {@link #noteCompiledBinding} 真正登记成功的次数。 */
    private static int probeBindingSyncHits;
    /** {@link #pipelinePath} 命中前端登记表的次数。 */
    private static int probeRegisteredHits;
    /** {@link #pipelinePath} 退回 debugLabel 并认出本 mod 管线的次数。 */
    private static int probeDebugLabelHits;

    /**
     * 登记一条<b>已经过 {@code RenderSystem} 编译/Iris 重定向</b>的管线。
     *
     * <h2>为什么不能只靠 {@link #notePipelineBinding}</h2>
     * <p>{@code notePipelineBinding} 相信 {@code FrontendRenderPass#setPipeline} 收到的
     * 那个对象的 {@code name()}。但光影激活时<b>这个前提不成立</b>：
     * {@code RenderSystem#getCompiledPipeline} 被 Iris 的 {@code redirectIrisProgram}
     * 接管（2026-09-19 实机栈：{@code RenderSystem.getCompiledPipeline:133} →
     * {@code handler$…$iris$redirectIrisProgram:606}），凡是被
     * {@code assignScopePipelineToHand} 映射掉的管线都会换成 Iris 自己那条
     * {@code CompiledRenderPipeline}。日志把这件事写得明明白白：</p>
     * <pre>
     * Found perfect program match for tacz:pipeline/scope_body_clipped: HAND_CUTOUT
     * </pre>
     * <p>于是这些管线的 {@code name()} 不再是 {@code tacz:pipeline/…}，
     * {@code stripModNamespace} 返回 null，{@link #notePipelineBinding} 直接 return ——
     * 恰好是<b>最需要裁剪的那几条</b>一条都进不了表。反过来，
     * {@code tacz:pipeline/scope_mask} 因为 Iris 没有 override（日志里那句
     * "Missing program tacz:pipeline/scope_mask in override list"）而保留了本 mod 的名字，
     * 成了唯一被登记的条目 —— 这正是上一轮探针
     * {@code firstTaczPipeline=pipeline/scope_mask, nonZeroMode=0} 的成因：
     * 唯一认出来的管线偏偏是掩码自己，而它的 mode 本来就该是 0。</p>
     *
     * <h2>做法</h2>
     * <p>不猜名字，<b>自己走一遍同一条重定向</b>：调用方
     * （{@code ScopeBodyRenderTypes#syncIrisPipelineBindings}）对每条 scope 管线调
     * {@code RenderSystem#getCompiledPipelineNullable}，拿到的正是绘制时落进
     * {@code GlRenderPass.pipeline} 的那个后端对象；名字则由调用方按管线常量直接给出，
     * 完全不依赖 {@code name()}。这样无论 Iris 怎么替换程序，映射都成立。</p>
     *
     * @param compiledPipeline {@code CompiledRenderPipeline}（可为 null，表示尚未编译）
     * @param expectedPath     不含命名空间的管线路径，如 {@code pipeline/scope_body_clipped}
     */
    public static void noteCompiledBinding(@Nullable Object compiledPipeline, String expectedPath) {
        if (compiledPipeline == null || expectedPath == null) {
            return;
        }
        probeBindingSyncAttempts++;
        try {
            // 每个掩码帧只跑 5 次（管线数是常数），不值得为此缓存 Method。
            Object backend = invokeNoArgs(compiledPipeline, "backendRenderPipeline");
            if (backend == null) {
                return;
            }
            if (NAME_BY_BACKEND_PIPELINE.size() >= NAME_CACHE_LIMIT) {
                NAME_BY_BACKEND_PIPELINE.clear();
                MODE_BY_PIPELINE.clear();
            }
            // 后端对象可能变（重载资源/切光影包），每次都覆写，别用 putIfAbsent。
            NAME_BY_BACKEND_PIPELINE.put(backend, expectedPath);
            // 旧管线实例可能记着过期的 0，一并作废，让它按新映射重算。
            MODE_BY_PIPELINE.remove(backend);
            probeBindingSyncHits++;
            probeRegisteredPaths.add(expectedPath);
        } catch (Throwable t) {
            logOnce("record compiled pipeline binding", t);
        }
    }

    /** 已登记成功的管线路径（仅用于探针输出）。 */
    private static final java.util.Set<String> probeRegisteredPaths =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private static Class<?> cachedFrontendClass;
    private static Method cachedFrontendNameMethod;
    private static Method cachedFrontendBackendMethod;

    /** 第一次解析出来的本 mod 管线路径，用于在匹配失败时暴露真实字符串。 */
    private static volatile String probeFirstTaczPath;
    /** 第一次见到的任意管线 label（含非 tacz），用于确认取法本身通不通。 */
    private static volatile String probeFirstAnyLabel;
    private static boolean loggedProbe;

    /** 供 {@code IrisShaderCreatorMixin} 上报「源码注入确实做成了几个 HAND 程序」。 */
    public static void noteHandProgramPatched() {
        probeHandProgramsPatched++;
    }

    /**
     * 首次开镜后汇总一次链路状态。
     *
     * <p>由 {@code ScopeMaskRenderer} 在确认「本帧画了掩码」之后调用；
     * 只打一行，之后不再打扰。读这行就能定位断点：</p>
     * <ul>
     *   <li>{@code handProgramsPatched=0} → IrisShaderCreatorMixin 没往任何 HAND
     *       程序里注入 tacz 分支（@ModifyArgs 是 require=0 的软注入，失败也不报错）；
     *       此时后面几项必然全是 0，先查这一项；</li>
     *   <li>{@code shaderSetup=0} → IrisExtendedShaderMixin 没装上
     *       （Iris 又改了 iris$setupState 的签名/方法名）；</li>
     *   <li>{@code renderPass=0} → IrisGlCommandEncoderMixin 没装上
     *       （setupDraw 又改名了）；这两个都是 require=0 的软注入，不会报错；</li>
     *   <li>{@code nonZeroMode=0} → 两个 hook 都在跑，但没认出我们的管线
     *       （resolveMode 靠 pipeline location 字符串匹配，Iris 换了取法）；</li>
     *   <li>{@code noModeUniform>0} → 认出来了，但 Iris 的着色器里没有
     *       tacz_ScopeMaskMode 这个 uniform，即 IrisShaderCreatorMixin 的
     *       源码注入没生效（它是 @ModifyArgs，失败同样静默）；</li>
     *   <li>{@code noMaskTexture>0} → 前面都对，但掩码纹理没拿到；</li>
     *   <li>{@code modeWritten>0} → 整条链路通了，问题在着色器逻辑本身。</li>
     * </ul>
     */
    public static void logProbeOnce() {
        if (loggedProbe) {
            return;
        }
        // 只在「链路已经跑通」或「已经攒够样本足以判定失败」时才定版。
        // 首帧掩码画出来的那一刻，drawcall 可能还没轮到我们的管线，
        // 此时打一行全 0 会误导（上一轮就差点据此下错结论）。
        // modeWritten>0 = 通了，可以定版；否则等到 renderPass 累计够多再定。
        if (probeModeWritten == 0 && probeRenderPassCalls < 10_000) {
            return;
        }
        loggedProbe = true;
        GunMod.LOGGER.info("[TACZ Scope][PROBE] Iris scope-mask chain after first masked frame: "
                        + "handProgramsPatched={}, shaderSetup={}, renderPass={}, nonZeroMode={}, "
                        + "noModeUniform={}, noMaskTexture={}, modeWritten={}, "
                        + "firstTaczPipeline={}, firstAnyPipelineLabel={}, "
                        + "bindingSync={}/{}, registeredPaths={}, nameTableSize={}, "
                        + "pathFrom={table:{},debugLabel:{}}. "
                        + "(shaderSetup/renderPass == 0 means the corresponding Iris mixin did not apply; "
                        + "nonZeroMode == 0 means our pipelines were not recognised; "
                        + "noModeUniform > 0 means the shader-source injection did not take effect; "
                        + "bindingSync hits < attempts means getCompiledPipelineNullable returned null; "
                        + "registeredPaths should list the 6 clipped scope pipelines -- if it only lists "
                        + "pipeline/scope_mask then the FrontendRenderPass name() route was defeated by "
                        + "Iris program redirection and only the explicit sync is working.)",
                probeHandProgramsPatched, probeShaderSetupCalls, probeRenderPassCalls,
                probeNonZeroMode, probeNoModeUniform, probeNoMaskTexture, probeModeWritten,
                probeFirstTaczPath, probeFirstAnyLabel,
                probeBindingSyncHits, probeBindingSyncAttempts,
                String.join(",", probeRegisteredPaths), NAME_BY_BACKEND_PIPELINE.size(),
                probeRegisteredHits, probeDebugLabelHits);
    }

    /**
     * 本帧当前正在 setup 的 {@code GlRenderPass}，由 {@code IrisGlCommandEncoderMixin} 在
     * {@code GlCommandEncoder#trySetup} 的 <b>HEAD</b> 记下。
     *
     * <h3>为什么必须在 HEAD 记</h3>
     * Iris 的 {@code MixinGlCommandEncoder} 也在 {@code trySetup} 的 <b>RETURN</b> 注入，
     * 并在那里调用 {@code ExtendedShader#iris$setupState}：
     * <pre>
     * &#64;Inject(method = "trySetup", at = &#64;At("RETURN"))
     * private void iris$setupState(GlRenderPass glRenderPass, Collection&lt;String&gt; c, CallbackInfoReturnable&lt;Boolean&gt; cir) {
     *     if (glRenderPass.pipeline.program() instanceof IrisProgram is &amp;&amp; !is.iris$isSetUp()) {
     *         is.iris$setupState(glRenderPass.samplers, ...);   // ← _glUseProgram + samplers.update() + uniforms.update()
     *     }
     * }
     * </pre>
     * 也就是说「Iris 重新绑程序与采样器」和「我们写 mode」挂在<b>同一个注入点</b>上，
     * 谁先谁后完全由 mixin config 的应用顺序决定 —— 那是随已安装 mod 集合变化的，
     * 不是我们能控制的。HEAD 一定早于任何 RETURN 处理器，所以在那里抓 pass 是安全的，
     * 这样 {@code iris$setupState} RETURN 里要解析 mode 时，记下的 pass 已经就位。
     */
    private static Object currentPass;

    /**
     * {@code GlRenderPass.pipeline} 字段，按 class 缓存。
     *
     * <h3>为什么非缓存不可</h3>
     * {@link #applyToGlRenderPass} 挂在 {@code GlCommandEncoder.trySetup} 上，
     * 也就是<b>每一次 draw call 之前</b>都会跑一遍 —— 开着 Sodium + Iris，
     * 这是每帧成千上万次。原来那版每次都现查：
     * <pre>
     * target.getClass().getDeclaredField(name)   // 每次都新建一个 Field 副本
     * target.getClass().getMethod(name)          // 同上，且要走完整张公共方法表
     * </pre>
     * {@code getDeclaredField}/{@code getMethod} <b>每次调用都返回一份防御性拷贝</b>，
     * 于是每个 draw call 要付 5 次反射查找 + 5 次对象分配 + 5 次 setAccessible 访问检查。
     * 这笔钱与开不开镜无关，是<b>全程</b>都在付的。
     */
    private static Class<?> cachedPassClass;
    private static Field cachedPipelineField;
    private static boolean pipelineFieldResolved;

    /**
     * 「这套 GL 管线对应哪个 mode」的记忆。
     *
     * <p>一个 {@code GlRenderPipeline} 实例对应的 RenderPipeline location 是<b>固定</b>的，
     * 所以判定结果永远不变 —— 逐 draw call 重新用反射取一遍 location、
     * 再 {@code toLowerCase} 出一个新字符串来比较，纯属白花。
     * 按实例身份记住即可。
     */
    private static final java.util.Map<Object, Integer> MODE_BY_PIPELINE = new java.util.IdentityHashMap<>();
    /** 管线实例是有限的（几十个）；真出现异常增长就整体丢弃重来，避免无界增长。 */
    private static final int MODE_CACHE_LIMIT = 512;

    /** {@code GL_MAX_TEXTURE_IMAGE_UNITS} 是驱动常量，问一次就够。 */
    private static int cachedMaxTextureUnits = -1;

    /**
     * 每个被注入程序的掩码采样器 unit 记忆（见 {@link #ensureMaskUnit}）。
     * 按 program id 缓存；id 会复用，管线重建时由 {@link #onPipelineRebuild} 整体清空。
     */
    private static final java.util.Map<Integer, Integer> UNIT_BY_PROGRAM = new java.util.HashMap<>();
    /** 实在没有空闲 unit 的程序：采样器别名到主贴图 unit，且永远不许 mode≠0。 */
    private static final java.util.Set<Integer> NO_FREE_UNIT = new java.util.HashSet<>();
    /** 诊断表已打过的程序（SCOPE_MASK_DEBUG 开启时每个程序只打一次）。 */
    private static final java.util.Set<Integer> DIAG_DONE = new java.util.HashSet<>();

    /**
     * Iris 正在建（新）管线 —— 由 {@code IrisShaderCreatorMixin} 的 link 钩子每次调用。
     * program id / GlRenderPipeline 实例都会换代，按 id/实例缓存的记忆整体清空，
     * 下次 setup 按新状态重选（link 全部发生在 draw 之前，不存在"清掉正在用的"）。
     */
    public static void onPipelineRebuild() {
        UNIT_BY_PROGRAM.clear();
        NO_FREE_UNIT.clear();
        MODE_BY_PIPELINE.clear();
        DIAG_DONE.clear();
    }

    /** 注入策略（配置读失败回 HAND_ONLY = 默认行为，不静默断功能）。 */
    public static RenderConfig.IrisScopeMaskInjection injectionPolicy() {
        try {
            RenderConfig.IrisScopeMaskInjection policy = RenderConfig.IRIS_SCOPE_MASK_INJECTION.get();
            return policy == null ? RenderConfig.IrisScopeMaskInjection.HAND_ONLY : policy;
        } catch (Throwable t) {
            return RenderConfig.IrisScopeMaskInjection.HAND_ONLY;
        }
    }

    /**
     * 给被注入程序选一个没被任何其它采样器占用的 unit 并写入，一劳永逸
     * （uniform 值按程序对象持久，Iris/原版不认识这个名字所以永远不会碰它）。
     *
     * <p>为什么必须做：GL 采样器默认值是 unit 0，而 unit 0 在 Sodium 地形程序里是
     * {@code isamplerBuffer u_SectionTimeInfo} —— 不同类型采样器同 unit 是规范非法状态，
     * Apple 会在 draw 时静默丢弃（地形全透明案的根因链；HAND_ONLY 策略下地形程序根本
     * 不会被注入，这里是第二道保险，让 ALL 策略同样合法）。
     *
     * <p>必须在 Iris {@code ProgramSamplers#update()} 的 initializer 跑过之后调用 ——
     * 调用链 {@code applyToShaderProgram ← iris$setupState RETURN} 天然满足
     * （Iris 在 setupState 里先 _glUseProgram + samplers.update）。
     * 注意 {@code applyToGlRenderPass} 有"无掩码早退"，但 {@code applyToShaderProgram}
     * 没有 —— 每个程序第一次 setup 一定会经过这里，unit 一定会被落定。
     */
    private static int ensureMaskUnit(int program, int samplerLocation) {
        Integer remembered = UNIT_BY_PROGRAM.get(program);
        if (remembered != null) {
            return remembered;
        }
        if (cachedMaxTextureUnits < 0) {
            cachedMaxTextureUnits = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
        }
        java.util.BitSet used = new java.util.BitSet(cachedMaxTextureUnits);
        int n = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORMS);
        int maxLen = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        // 本仓库 LWJGL 的 String 版 glGetActiveUniform 只接受 IntBuffer（无 int[] 重载）。
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int i = 0; i < n; i++) {
                String name = GL20C.glGetActiveUniform(program, i, maxLen, size, type);
                if (name == null || !isSamplerType(type.get(0))) {
                    continue;
                }
                String base = name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;
                if (UNIFORM_SAMPLER.equals(base)) {
                    continue;
                }
                int loc = GL20C.glGetUniformLocation(program, base);
                for (int k = 0; loc >= 0 && k < size.get(0); k++) {
                    int u = GL20C.glGetUniformi(program, loc + k);
                    if (u >= 0 && u < cachedMaxTextureUnits) {
                        used.set(u);
                    }
                }
            }
        }
        // 从高位往下找空闲 unit（Iris 动态采样器从 3 往上分配，原版占低位）。
        int unit = -1;
        for (int u = cachedMaxTextureUnits - 1; u >= 0; u--) {
            if (!used.get(u)) {
                unit = u;
                break;
            }
        }
        if (unit < 0) {
            // 没有空闲 unit：退回主贴图所在 unit（同为 sampler2D → 合法），
            // 但此程序不允许启用 mode≠0（否则会顶掉主贴图）。
            int texLoc = firstLocation(program, "gtexture", "tex", "texture", "Sampler0", "u_BlockTex");
            unit = texLoc >= 0 ? GL20C.glGetUniformi(program, texLoc) : 0;
            NO_FREE_UNIT.add(program);
            GunMod.LOGGER.warn("[TACZ Scope] program {} has no free texture unit for the scope mask; aliasing to unit {} and disabling clipping for it.", program, unit);
        }
        // 调用方保证 program 就是当前程序（两处调用点都校验过）。
        GL20C.glUniform1i(samplerLocation, unit);
        UNIT_BY_PROGRAM.put(program, unit);
        return unit;
    }

    /** 是否采样器类型。区间取保守（宁可多圈进来一个非采样器、少占一个 unit，也不漏掉真采样器去撞 unit）。 */
    private static boolean isSamplerType(int type) {
        return (type >= 0x8B5D && type <= 0x8B6C)
                || (type >= 0x8DC0 && type <= 0x8DD8)
                || (type >= 0x9108 && type <= 0x910D);
    }

    private static int firstLocation(int program, String... names) {
        for (String name : names) {
            int loc = GL20C.glGetUniformLocation(program, name);
            if (loc >= 0) {
                return loc;
            }
        }
        return -1;
    }

    /**
     * SCOPE_MASK_DEBUG 开启时：每个程序第一次 setup 打印采样器 unit 表 + validate 结果。
     * 必须在 {@code writeScopeMaskState} 之前调 —— 之后抓到的就是被修过的状态。
     * 类型码：0x8B5E=sampler2D，0x8B62=sampler2DShadow，0x8DC2=samplerBuffer，
     * 0x8DD0=isamplerBuffer，0x8DD8=usamplerBuffer。
     */
    static void diagSamplerTable(int program) {
        if (!DIAG_DONE.add(program)) {
            return;
        }
        int n = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORMS);
        int maxLen = GL20C.glGetProgrami(program, GL20C.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        StringBuilder sb = new StringBuilder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int i = 0; i < n; i++) {
                String name = GL20C.glGetActiveUniform(program, i, maxLen, size, type);
                if (name == null || !isSamplerType(type.get(0))) {
                    continue;
                }
                String base = name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;
                int loc = GL20C.glGetUniformLocation(program, base);
                if (loc < 0) {
                    continue;
                }
                sb.append(base).append(":0x").append(Integer.toHexString(type.get(0)))
                        .append("@u").append(GL20C.glGetUniformi(program, loc)).append("  ");
            }
        }
        GL20C.glValidateProgram(program);
        boolean ok = GL20C.glGetProgrami(program, GL20C.GL_VALIDATE_STATUS) == 1;
        String log = GL20C.glGetProgramInfoLog(program).trim();
        GunMod.LOGGER.info("[TACZ Scope][diag] program={} samplers=[{}] validate={} log='{}'", program, sb, ok ? "OK" : "FAIL", log);
    }

    /**
     * 把掩码纹理绑到给定 unit，并把 GL 状态原样恢复。
     *
     * <p>老代码最后无条件 {@code glActiveTexture(GL_TEXTURE0)} —— 真实 active 变了，
     * 而 {@code GlStateManager.activeTexture} 缓存还记着旧值，此后原版/Iris 的
     * {@code _activeTexture(N)} 会被缓存短路、{@code _bindTexture} 绑错单元
     * （开镜期贴图错位）。这里读写都用裸 GL，但恢复的是进入时的真实值
     * （此前无人绕开 GlStateManager，它与缓存一致），等价于 Iris 自己的恢复手法。
     */
    private static void bindMaskTexture(int unit, int textureId) {
        int prevActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureId);
        // sampler object 是按 unit 的全局状态：即使本程序没人用这个 unit，
        // 也可能残留别家程序绑的 sampler（比如 shadow-compare），会覆盖纹理自身
        // 参数甚至让采样未定义 —— 解绑，退回纹理自身参数。
        // （掩码的 NEAREST/Clamp 采样器只活在 vanilla 管线那条路上
        // ScopeMaskTextureHandle；Iris 路这里拿不到它的 GL id，
        // 用纹理自身参数是安全退路。）
        GL33C.glBindSampler(unit, 0);
        GL13C.glActiveTexture(prevActive);
    }

    private IrisScopeMaskState() {
    }

    /** 记下本帧当前的 render pass。挂在 {@code trySetup} HEAD，见 {@link #currentPass}。 */
    public static void setCurrentPass(Object glRenderPass) {
        currentPass = glRenderPass;
    }

    /**
     * Iris 每做一次 {@code ExtendedShader#iris$setupState} 就调一次。
     *
     * <h2>【顺序无关加固】不再无脑写 0</h2>
     * 旧实现在这里把 {@code tacz_ScopeMaskMode} 一律复位成 0。但
     * {@code iris$setupState} 是被 Iris 的 {@code trySetup} RETURN 处理器调起来的，
     * 而我们写 mode 的 {@link #applyToGlRenderPass} 也挂在 {@code trySetup} RETURN 上 ——
     * <b>同一个注入点的两个处理器，执行顺序由 mixin config 应用顺序决定</b>。
     * 一旦我们的处理器排在 Iris 之前，顺序就变成：
     * <ol>
     *   <li>我们写 mode = 1 / 2；</li>
     *   <li>Iris 的处理器跑 {@code iris$setupState} → {@code _glUseProgram} +
     *       {@code samplers.update()} + 本方法 → <b>mode 被写回 0</b>；</li>
     *   <li>此后同一 pass 内 {@code trySetup} 对同一条管线返回 false，
     *       我们的处理器不再被调用 —— mode 就一直是 0。</li>
     * </ol>
     * 结果：整个 pass 的镜身与准星都不裁。装不装第三方 mod 会改变 mod 发现顺序，
     * 从而改变这两个处理器的先后，所以症状看起来像被别的 mod「触发」。
     *
     * <p>现在改成：在这里<b>按当前 pass 写正确的 mode</b>（非镜身/准星管线自然就是 0，
     * 防泄漏语义不变）。配合 {@link #applyToGlRenderPass} 也在 RETURN 写一次，
     * 两处谁最后跑都得到正确值 —— 与 mixin 应用顺序无关。</p>
     */
    public static void applyToShaderProgram(Object shader) {
        probeShaderSetupCalls++;
        try {
            int programId = getProgramId(shader);
            if (programId <= 0) {
                return;
            }
            // iris$setupState 开头就做了 _glUseProgram(getProgramId())，
            // 所以这里当前程序就是它。不一致就说明调用点变了 —— 宁可不写，
            // 也不能把 A 程序的 location 写进 B 程序（glUniform1i 只作用于当前程序）。
            if (GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM) != programId) {
                if (!loggedProgramMismatch) {
                    loggedProgramMismatch = true;
                    GunMod.LOGGER.warn("[TACZ Scope] Iris program setup ran with a different program bound "
                            + "(expected={}, current={}); skipping the scope-mask uniform write for this setup.",
                            programId, GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM));
                }
                return;
            }
            if (RenderConfig.SCOPE_MASK_DEBUG != null && RenderConfig.SCOPE_MASK_DEBUG.get()) {
                // 诊断位：在 Iris/原版落定、我们写入之前抓一张采样器 unit 表 + validate。
                // 位置必须在 writeScopeMaskState 之前 —— 之后抓到的就是被 ensureMaskUnit
                // 修过的状态，unit 0 上到底是谁就看不见了。
                diagSamplerTable(programId);
            }
            // resolveMode 命中管线→mode 记忆（MODE_BY_PIPELINE），别绕开缓存层。
            writeScopeMaskState(programId, resolveMode(currentPass), currentPass);
        } catch (Throwable t) {
            logOnce("apply scope mask on Iris program setup", t);
        }
    }

    /**
     * Updates the active Iris shader program uniforms for the current GlRenderPass draw call.
     * If the draw call is {@code scope_body_clipped}, mode is set to 1.
     * If the draw call is {@code scope_reticle_clipped}, mode is set to 2.
     * Otherwise (gun body, attachments, hands, entities, particles), mode is set to 0.
     */
    public static void applyToGlRenderPass(Object glRenderPass) {
        probeRenderPassCalls++;
        try {
            if (glRenderPass == null) {
                return;
            }
            // 【快速路径 —— 本方法每次 draw call 都会被调到】
            //
            // mode 只可能在「本帧画了目镜掩码」的帧上变成非 0。既没开镜、上一帧也没开镜，
            // 就不存在任何需要写的 uniform，也不存在需要擦掉的残留 —— 直接回。
            //
            // 为什么「上一帧」也要算进去：Iris 把我们的 scope_body / scope_reticle 管线
            // 映射到它的 HAND 程序上，也就是<b>同一个 GL program</b> 既画镜身（mode=1）
            // 也画枪和手（mode=0）。松开右键的<b>那一帧</b>必须照常跑完整流程，
            // 把这些程序里残留的 mode 擦回 0，否则枪身会带着上一帧的裁剪继续画。
            // 擦干净之后（再下一帧起）uniform 会一直保持 0，于是可以安心早退。
            //
            // 收益：不开镜时，每个 draw call 的开销从「5 次反射 + 2 次 GL 查询」
            // 降到两次布尔读取。这条路径与开不开镜无关地跑在<b>每一帧</b>上，
            // 所以这就是「没开镜时帧数也差」的那一份。
            if (!ScopeMaskRenderer.hasMaskThisFrame() && !ScopeMaskRenderer.hadMaskLastFrame()) {
                return;
            }
            int mode = resolveMode(glRenderPass);

            // 【顺序无关加固】uniform 的写入目标只能是【当前程序】——
            // glUniform1i 作用于 glUseProgram 绑定的那个程序，而 uniform location
            // 是按程序分配的。旧实现在 GL_CURRENT_PROGRAM 为 0 时退回
            // 「从 glRenderPass.pipeline.program() 取 programId」，然后拿
            // 【那个程序】的 location 去调 glUniform1i —— 那是把 A 程序的
            // location 写进 B 程序（或写进空气），静默无效。现在没有当前程序就直接放弃，
            // 由 applyToShaderProgram 在 Iris 真正 setup 程序时补写。
            int programId = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (programId <= 0) {
                return;
            }
            writeScopeMaskState(programId, mode, glRenderPass);
        } catch (Throwable t) {
            logOnce("apply scope mask to GL render pass", t);
        }
    }

    /**
     * 把 mode / 掩码采样器写进<b>已经绑定为当前程序</b>的 {@code programId}。
     *
     * <p>两个调用点（{@code trySetup} RETURN 与 {@code iris$setupState} RETURN）共用这一份，
     * 保证「最后跑的那个」写的是同一套状态。</p>
     */
    private static void writeScopeMaskState(int programId, int mode, Object glRenderPass) {
        if (mode != 0) {
            probeNonZeroMode++;
        }
        int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
        if (modeLocation < 0) {
            // 这个程序没有被注入过 tacz 分支（HAND_ONLY 下绝大多数 Iris 程序都是这种），直接走人。
            // 探针只统计「本该裁剪却找不到 uniform」的情形 —— mode==0 的程序绝大多数
            // 本来就不该有这个 uniform，计进去会把信号淹没。
            if (mode != 0) {
                probeNoModeUniform++;
            }
            return;
        }
        int samplerLocation = GL20C.glGetUniformLocation(programId, UNIFORM_SAMPLER);
        // ↓ mode==0 也要走到这里：被注入程序的采样器【永远】不能停在 GL 默认的 unit 0。
        // （unit 0 在 Sodium 地形程序里是 isamplerBuffer —— 不同类型同 unit 则 Apple 丢 draw。）
        int unit = samplerLocation >= 0 ? ensureMaskUnit(programId, samplerLocation) : -1;
        if (mode == 0 || samplerLocation < 0 || NO_FREE_UNIT.contains(programId)) {
            GL20C.glUniform1i(modeLocation, 0);
            return;
        }
        int textureId = resolveMaskTextureId(glRenderPass);
        if (textureId <= 0) {
            probeNoMaskTexture++;
            GL20C.glUniform1i(modeLocation, 0);
            return;
        }
        if (!loggedApply) {
            loggedApply = true;
            GunMod.LOGGER.info("[TACZ Scope] Iris scope-mask bridge active (mode={}, textureUnit={}, textureId={}).", mode, unit, textureId);
        }
        // 顺序：先写 uniform，再绑纹理（bindMaskTexture 内部负责把 active 单元恢复原状）。
        // Iris 的 ProgramSamplers#update() 跑在我们之前且只重绑它自己那几个单元，
        // 所以我们这一次绑定是本轮最后的写入者。
        probeModeWritten++;
        GL20C.glUniform1i(modeLocation, mode);
        bindMaskTexture(unit, textureId);
    }

    /**
     * {@code GlRenderPass.pipeline}，字段对象按 class 缓存一次。
     *
     * <p>运行期这个 class 实际上恒定，所以「上次是哪个 class」比一下就够，
     * 不必上 map。见 {@link #cachedPipelineField} 的注释。
     */
    private static Field pipelineField(Object glRenderPass) {
        Class<?> cls = glRenderPass.getClass();
        if (cls != cachedPassClass || !pipelineFieldResolved) {
            cachedPassClass = cls;
            cachedPipelineField = null;
            for (Class<?> c = cls; c != null && cachedPipelineField == null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("pipeline");
                    f.setAccessible(true);
                    cachedPipelineField = f;
                } catch (NoSuchFieldException ignored) {
                    // 继续往父类找
                }
            }
            pipelineFieldResolved = true;
        }
        return cachedPipelineField;
    }

    private static int resolveMode(Object glRenderPass) {
        try {
            if (glRenderPass == null) {
                return 0;
            }
            Field pipelineField = pipelineField(glRenderPass);
            if (pipelineField == null) {
                return 0;
            }
            Object glPipeline = pipelineField.get(glRenderPass);
            if (glPipeline == null) {
                return 0;
            }
            // 同一个管线实例的判定结果恒定，记住即可 —— 省掉后面那四次反射
            // 与一次 toLowerCase 分配。
            Integer remembered = MODE_BY_PIPELINE.get(glPipeline);
            if (remembered != null) {
                return remembered;
            }
            int resolved = resolveModeUncached(glPipeline);
            if (MODE_BY_PIPELINE.size() >= MODE_CACHE_LIMIT) {
                MODE_BY_PIPELINE.clear();
            }
            MODE_BY_PIPELINE.put(glPipeline, resolved);
            return resolved;
        } catch (Throwable t) {
            logOnce("resolve scope render pass", t);
        }
        return 0;
    }

    /** 真正去问「这套管线是不是我们的镜身/准星管线」。只在每个管线实例上跑一次。 */

    /**
     * 取出这条后端管线对应的 {@code tacz:pipeline/xxx} 路径段（不含命名空间），
     * 取不到或不属于本 mod 时返回 {@code null}。
     *
     * <h2>26.3 为什么要换取法</h2>
     * <p>26.2 走的是 {@code GlRenderPipeline#info()} → {@code RenderPipeline#getLocation()}。
     * <b>26.3 的 {@code GlRenderPipeline} 已经没有 {@code info()} 了</b> ——
     * 它只留下 device/program/vertexArray 等纯 GL 状态，前端的
     * {@code RenderPipeline} 引用整个不再持有（已对照 26.3 反编译源确认）。
     * 反射拿不到方法 → {@code invokeNoArgs} 返回 null → resolveMode 恒返回 0
     * → 光影下 mode 永远是 0 → 不裁剪。
     * 这正是探针 {@code nonZeroMode=0}（而 shaderSetup=80659、renderPass=447454
     * 都在正常跳动）所指向的断点。</p>
     *
     * <h2>新取法的同源性</h2>
     * <p>改读 {@code program().getDebugLabel()}。这个字符串不是调试用的花名，
     * 而是管线 location 本身：{@code PipelineBuilder} 构造后端
     * {@code CreateInfo} 时写的就是 {@code pipeline.getLocation().toString()}
     * （PipelineBuilder:346-347），{@code GlPipelineRecompiler} 再把它原样传给
     * {@code GlProgram.link(..., createInfo.name())}（GlPipelineRecompiler:314），
     * 最终由 {@code getDebugLabel()} 返回。所以它形如
     * {@code "tacz:pipeline/scope_body_clipped"}，与旧路径拿到的 location 等价。</p>
     *
     * <p>保留旧路径作为回退：26.2 没有 debugLabel 这条链，而本类同样被
     * 26.2 分支使用；两条都试一次，谁先成功用谁。</p>
     */
    @Nullable
    private static String pipelinePath(Object glPipeline) {
        // —— 26.3 主路径：查前端登记表 ——
        // 这是唯一在光影下也成立的取法，见 NAME_BY_BACKEND_PIPELINE 的说明。
        String registered = NAME_BY_BACKEND_PIPELINE.get(glPipeline);
        if (registered != null) {
            probeRegisteredHits++;
            return registered;
        }
        // 下面两条是历史取法，26.3 光影下都拿不到我们的 location，
        // 仅为兼容 26.2 与「未装 Iris 时后端 label 恰好就是 location」的情形保留。
        // 各自 try：invokeNoArgs 底层是 getMethod，方法不存在会抛
        // NoSuchMethodException 而不是返回 null。
        // —— 回退一：program().getDebugLabel() ——
        try {
            Object program = invokeNoArgs(glPipeline, "program");
            if (program != null) {
                Object label = invokeNoArgs(program, "getDebugLabel");
                if (label != null) {
                    if (probeFirstAnyLabel == null) {
                        probeFirstAnyLabel = String.valueOf(label);
                    }
                    String path = stripModNamespace(String.valueOf(label));
                    if (path != null) {
                        probeDebugLabelHits++;
                        if (probeFirstTaczPath == null) {
                            probeFirstTaczPath = path;
                        }
                        return path;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 落到下面的回退路径
        }
        // —— 26.2 回退路径：info().getLocation() ——
        try {
            Object renderPipeline = invokeNoArgs(glPipeline, "info");
            if (renderPipeline == null) {
                return null;
            }
            Object location = invokeNoArgs(renderPipeline, "getLocation");
            if (location == null) {
                return null;
            }
            String namespace = String.valueOf(invokeNoArgs(location, "getNamespace"));
            if (!GunMod.MOD_ID.equals(namespace)) {
                return null;
            }
            return String.valueOf(invokeNoArgs(location, "getPath")).toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * {@code "tacz:pipeline/scope_body_clipped"} → {@code "pipeline/scope_body_clipped"}；
     * 不是本 mod 的命名空间就返回 null。
     */
    @Nullable
    private static String stripModNamespace(String label) {
        String prefix = GunMod.MOD_ID + ":";
        if (!label.startsWith(prefix)) {
            return null;
        }
        return label.substring(prefix.length()).toLowerCase(Locale.ROOT);
    }

    private static int resolveModeUncached(Object glPipeline) {
        try {
            String normalized = pipelinePath(glPipeline);
            if (normalized == null) {
                return 0;
            }
            if (BODY_PIPELINE.equals(normalized)) {
                // 【恒为 1】镜身在孔径内 discard，于是最终画面里孔径那块就是 1× 的世界。
                //
                // 镜内的「放大」不在这里做 —— 那是
                // {@code ScopePipRenderer.compositeAfterLevelUnderShaders()} 的活：
                // 等 Iris 整条管线跑完，直接在最终画面上把孔径内那 1/Z 的小块放大铺满。
                // 而「孔径内是干净的 1× 世界、没有枪」正是这里 discard 换来的前提。
                //
                // ↓ 以下是被推翻的旧方案，留作路标，别再走一遍 ↓
                // 曾经让这里返回 3，由注入进 pack 着色器的分支去采样 colortexN。
                // 两次实测都失败：先是纯黑（HAND_CUTOUT 跑在延迟光照之前，
                // 那一刻没有任何 colortex 装着已着色的场景），把枪挪进半透明 pass 之后
                // 又变成「灰噪块 + 黑」（场景色逐 pack 不同，Eclipse 在 colortex2）。
                // 根子上这条路要求猜中别家 pack 的内部约定，怎么修都是下一次盲猜。
                return 1;
            }
            if (FLASH_TRANSLUCENT_PIPELINE.equals(normalized)
                    || FLASH_SWIRL_PIPELINE.equals(normalized)) {
                return 1;
            }
            if (RETICLE_PIPELINE.equals(normalized) || RETICLE_EMISSIVE_PIPELINE.equals(normalized)) {
                return 2;
            }
            if (TEXT_PIPELINE.equals(normalized)) {
                // 【镜内文字，2026-08-30 补】与准星同侧：discard 镜外、只留镜内。
                // 光影下我们自己的 scope_text.fsh 不会运行（assignPipeline 之后
                // Iris 用 pack 的 HAND 着色器替换整条管线），裁剪只能靠注入分支
                // 的 mode=2。此前这里没有该管线的映射 → mode 恒 0 → MK5HD 等
                // 瞄具的镜内文字在光影下不裁切（用户实测截图，2026-08-30）。
                return 2;
            }
        } catch (Throwable t) {
            logOnce("resolve scope render pass", t);
        }
        return 0;
    }

    private static int resolveMaskTextureId(Object glRenderPass) {
        try {
            Object samplersObj = readField(glRenderPass, "samplers");
            if (samplersObj instanceof Map<?, ?> samplers) {
                Object tvs = samplers.get(MASK_SAMPLER);
                if (tvs != null) {
                    int id = getGlTextureId(tvs);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            RenderTarget target = ScopeMaskTarget.current();
            if (target != null) {
                Object colorTex = target.getColorTexture();
                if (colorTex != null) {
                    int id = getGlTextureId(colorTex);
                    if (id > 0) {
                        return id;
                    }
                }
                Object colorTexView = target.getColorTextureView();
                if (colorTexView != null) {
                    int id = getGlTextureId(colorTexView);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static int getProgramId(Object shader) {
        try {
            if (shader == null) {
                return 0;
            }
            Method method = null;
            for (Class<?> c = shader.getClass(); c != null && method == null; c = c.getSuperclass()) {
                try {
                    method = c.getDeclaredMethod("getProgramId");
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (method == null) {
                return 0;
            }
            method.setAccessible(true);
            Object id = method.invoke(shader);
            if (id instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable t) {
            logOnce("resolve shader program id", t);
        }
        return 0;
    }

    private static int getGlTextureId(Object obj) {
        if (obj == null) {
            return 0;
        }
        try {
            if (obj.getClass().getSimpleName().contains("TextureViewAndSampler")) {
                Object view = invokeNoArgs(obj, "view");
                return getGlTextureId(view);
            }
            try {
                Method glIdMethod = obj.getClass().getMethod("glId");
                glIdMethod.setAccessible(true);
                Object id = glIdMethod.invoke(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method irisGlIdMethod = obj.getClass().getMethod("iris$getGlId");
                irisGlIdMethod.setAccessible(true);
                Object id = irisGlIdMethod.invoke(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method textureMethod = obj.getClass().getMethod("texture");
                textureMethod.setAccessible(true);
                Object tex = textureMethod.invoke(obj);
                if (tex != null && tex != obj) {
                    int id = getGlTextureId(tex);
                    if (id > 0) {
                        return id;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Field idField = obj.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object id = idField.get(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchFieldException ignored) {
            }
        } catch (Throwable t) {
            logOnce("extract texture id", t);
        }
        return 0;
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void logOnce(String action, Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            GunMod.LOGGER.warn("[TACZ Scope] Iris scope-mask bridge failed to {}. Scope clipping will fall back for this draw.", action, t);
        }
    }
}
