package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import com.tacz.guns.config.client.RenderConfig;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        int[] size = new int[1];
        int[] type = new int[1];
        for (int i = 0; i < n; i++) {
            String name = GL20C.glGetActiveUniform(program, i, maxLen, size, type);
            if (name == null || !isSamplerType(type[0])) {
                continue;
            }
            String base = name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;
            if (UNIFORM_SAMPLER.equals(base)) {
                continue;
            }
            int loc = GL20C.glGetUniformLocation(program, base);
            for (int k = 0; loc >= 0 && k < size[0]; k++) {
                int u = GL20C.glGetUniformi(program, loc + k);
                if (u >= 0 && u < cachedMaxTextureUnits) {
                    used.set(u);
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
        int[] size = new int[1];
        int[] type = new int[1];
        for (int i = 0; i < n; i++) {
            String name = GL20C.glGetActiveUniform(program, i, maxLen, size, type);
            if (name == null || !isSamplerType(type[0])) {
                continue;
            }
            String base = name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;
            int loc = GL20C.glGetUniformLocation(program, base);
            if (loc < 0) {
                continue;
            }
            sb.append(base).append(":0x").append(Integer.toHexString(type[0]))
                    .append("@u").append(GL20C.glGetUniformi(program, loc)).append("  ");
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
        int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
        if (modeLocation < 0) {
            // 这个程序没有被注入过 tacz 分支（HAND_ONLY 下绝大多数 Iris 程序都是这种），直接走人。
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
    private static int resolveModeUncached(Object glPipeline) {
        try {
            Object renderPipeline = invokeNoArgs(glPipeline, "info");
            if (renderPipeline == null) {
                return 0;
            }
            Object location = invokeNoArgs(renderPipeline, "getLocation");
            if (location == null) {
                return 0;
            }
            String namespace = String.valueOf(invokeNoArgs(location, "getNamespace"));
            String path = String.valueOf(invokeNoArgs(location, "getPath"));
            if (!GunMod.MOD_ID.equals(namespace)) {
                return 0;
            }
            String normalized = path.toLowerCase(Locale.ROOT);
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
