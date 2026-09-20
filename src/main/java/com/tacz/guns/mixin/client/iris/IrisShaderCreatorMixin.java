package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import com.tacz.guns.config.client.RenderConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Injects a dormant TACZ scope-mask branch into Iris HAND fragment shaders before they are linked.
 *
 * <p>【作用域：只碰 HAND 程序 —— 开光影后世界透明案的 TACZ 侧修复】
 * 本注入曾经作用于 Iris 链接的<b>所有</b> fragment 程序（地形/实体/天空/阴影……）。
 * 实测结论（2026-09，多名玩家，Apple M4 + 任意 shaderpack）：只要 TACZ 在场，
 * 开光影后地形/世界即透明，移除 TACZ 即恢复 —— 而被注入的程序在日志里全部编译通过，
 * 且世界程序的 {@code tacz_ScopeMaskMode} 在运行期永远只会被写 0（分支永不执行）。
 * 也就是说：注入体对世界程序<b>零语义贡献</b>，却改变了它们的源码文本。
 * 自本修订起，只对 Iris 的 HAND 系程序（ShaderKey {@code HAND_*}：第一人称手部/
 * 枪械/瞄具管线，以及 pack 缺失对应程序时 Iris 合成的 fallback 程序）做注入；
 * 世界程序保持与原生 Iris<b>逐字节一致</b>，连 dormant 的 uniform 写入都不再有
 * （{@code IrisScopeMaskState} 查不到 uniform location 会直接跳过）。
 *
 * <p>瞄具裁剪不受影响：全部 scope 管线（{@code tacz:pipeline/scope_*_clipped}）
 * 都经 {@code IrisCompat.assignScopePipelineToHand} 显式钉在 Iris HAND 程序上；
 * 而枪身/手/世界绘制本来就只需要 mode=0（= 不裁剪 = uniform 默认值），
 * 注不注入结果都一样。
 *
 * <p>【钩子点：link 内部的 createShader 调用 —— name 与 source 在同一调用里相会】
 * 不 hook create*，不建跨方法桥：对 {@code link} 方法体内的
 * {@code createShader(String, ShaderType, String)} 调用做 {@code @ModifyArgs}，
 * 只处理 {@code ShaderType.FRAGMENT} 那一次（按类型守卫，不按 ordinal，
 * 对 Iris 增删着色器阶段免疫）。name（arg 0）与 fragment 源码（arg 2）
 * 在同一个调用里，天然同线程、无时序问题 —— 与 26.1.2 分支的
 * {@code IrisDepthRestoreShaderMixin} 同一手法（该分支已验证）。
 *
 * <p>【HAND 判定：name 含 hand（忽略大小写）】
 * 已知的两种命名（{@code hand_cutout} 系与 {@code gbuffers_hand} 系）全含 hand。
 * 误伤方向安全：多注一个世界程序只是回到老行为（且 C-2 保证 unit 合法）；
 * 漏注 hand 程序则会被下述告警抓住。
 *
 * <p>【失败只许大声】
 * hook 没装上（Iris 改名）→ 没有任何拦截计数，汇总行永远不出现；
 * 过滤器漏掉全部 hand（命名约定变了）→ 拦截 ≥20 个 fragment 却 0 hand 注入时
 * 一次性 WARN。没有静默的坏方向。
 *
 * <p>【为什么没有 ThreadLocal 桥了】
 * 上一版用 create*-HEAD → link 的 ThreadLocal 传 key，实机（Iris 1.11.2+mc26.2）
 * 77/77 无上下文 fail-open —— 桥一次都没接上（疑版本 skew + require=0 静默，
 * 根因未定，见 sync guide §6.7）。教训：require=0 的钩子必须自带存活计数；
 * 能单点取齐的判定，绝不用跨方法桥。
 *
 * <p>配置 {@code IrisScopeMaskInjection}（HAND_ONLY / ALL / OFF，默认 HAND_ONLY）：
 * OFF = 彻底关闭做对照实验；ALL = 旧行为（全注，靠每程序选 unit 保证合法）。
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisShaderCreatorMixin {
    // 注意：这里曾经有一座 create* → link 的 ThreadLocal 桥（传 ShaderKey 名），
    // 实机 77/77 失灵后已删除 —— 判定只用 createShader 调用自带的参数。

    // 目标描述符与 26.1.2 分支的 IrisDepthRestoreShaderMixin 逐字一致
    // （26.2 HEAD 源码核对：link 内按 vertex/geometry/tessControl/tessEval/fragment
    // 顺序调 createShader —— 但下面按 ShaderType 守卫，不依赖 ordinal）。
    @ModifyArgs(
            method = "link",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;createShader(Ljava/lang/String;Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;)I"),
            require = 0)
    private static void tacz$patchFragmentAtCreation(Args args) {
        Object shaderType = args.get(1);
        if (!(shaderType instanceof Enum<?> stage) || !"FRAGMENT".equals(stage.name())) {
            return;
        }
        tacz$tally(SAW_FRAGMENT);
        // 每次 link（= 每个 fragment 一次）都意味着 Iris 正在建（新）管线：
        // program id 会复用，先把按 id 缓存的 unit/mode/诊断记忆整体清空。
        IrisScopeMaskState.onPipelineRebuild();
        String programName = args.get(0) instanceof String s ? s : String.valueOf(args.get(0));
        String source = args.get(2) instanceof String s ? s : null;
        if (source == null) {
            return;
        }

        RenderConfig.IrisScopeMaskInjection policy = IrisScopeMaskState.injectionPolicy();
        if (policy == RenderConfig.IrisScopeMaskInjection.OFF) {
            tacz$tally(SKIP_CONFIG);
            tacz$logDecision(programName, "skipped (IrisScopeMaskInjection=OFF)");
            tacz$maybeSummarize();
            return;
        }
        boolean legacyAll = policy == RenderConfig.IrisScopeMaskInjection.ALL;
        if (source.contains("tacz_ScopeMaskMode")) {
            // 幂等：同一份源码第二次被编译（正常流程不应发生，防未来 Iris 改流程）。
            tacz$logDecision(programName, "skipped (already contains the bridge)");
            tacz$maybeSummarize();
            return;
        }
        boolean hand = tacz$isHandProgram(programName);
        if (!legacyAll && !hand) {
            tacz$tally(SKIP_WORLD);
            tacz$logDecision(programName, "skipped (not a HAND program; left byte-identical)");
            tacz$maybeSummarize();
            return;
        }
        String patched = tacz$injectScopeMask(source, programName);
        if (patched == source) {
            tacz$tally(SKIP_NO_MAIN);
        } else if (!legacyAll || hand) {
            tacz$tally(INJECTED_HAND);
        } else {
            tacz$tally(INJECTED_ALL);
        }
        if (patched != source) {
            args.set(2, patched);
        }
        tacz$logDecision(programName, patched == source
                ? "skipped (no verifiable 'void main() {' found; fail-closed)"
                : (legacyAll && !hand ? "INJECTED (ALL policy)" : "INJECTED (HAND program)"));
        tacz$maybeSummarize();
    }

    /**
     * HAND 判定：program 名含 {@code hand}（忽略大小写）。
     *
     * <p>已知的两种命名（{@code hand_cutout} 系与 {@code gbuffers_hand} 系、
     * 任意大小写）全含 hand。保守方向是多注：多注一个世界程序只是回到老行为
     * （且 C-2 保证 unit 合法）；漏注则由汇总行的 0-hand 告警大声报出。
     */
    private static boolean tacz$isHandProgram(String programName) {
        return programName != null && programName.toLowerCase(Locale.ROOT).contains("hand");
    }

    private static final int INJECTED_HAND = 0;
    private static final int SKIP_WORLD = 1;
    private static final int SKIP_CONFIG = 2;
    private static final int SKIP_NO_MAIN = 3;
    private static final int INJECTED_ALL = 4;
    /** 拦截到的 FRAGMENT createShader 调用总数（hook 存活证明）。 */
    private static final int SAW_FRAGMENT = 5;
    /** 管线构建只发生在渲染线程；计数纯诊断，近似即可。 */
    private static final long[] TACZ_TALLY = new long[6];
    private static boolean tacz$warnedNoHand;
    private static long tacz$lastSummaryMs;
    /** 每个 program 名只告警一次（名是有限集，跨重建复用，不会无界增长）。 */
    private static final Set<String> TACZ_WARNED_NO_MAIN = Collections.synchronizedSet(new HashSet<>());

    private static void tacz$tally(int kind) {
        TACZ_TALLY[kind]++;
    }

    private static void tacz$logDecision(String programName, String decision) {
        if (GunMod.LOGGER.isDebugEnabled()) {
            GunMod.LOGGER.debug("[TACZ Scope] Iris program link: name={} -> {}", programName, decision);
        }
    }

    private static void tacz$maybeSummarize() {
        long now = System.currentTimeMillis();
        if (now - tacz$lastSummaryMs < 10_000L) {
            return;
        }
        tacz$lastSummaryMs = now;
        GunMod.LOGGER.info("[TACZ Scope] Iris scope-mask injection so far: {} HAND program(s) patched, "
                        + "{} world program(s) left byte-identical to stock Iris "
                        + "({} config-skipped, {} no-verifiable-main skipped, {} legacy-ALL injected; "
                        + "saw {} fragment shader(s)).",
                TACZ_TALLY[INJECTED_HAND], TACZ_TALLY[SKIP_WORLD],
                TACZ_TALLY[SKIP_CONFIG], TACZ_TALLY[SKIP_NO_MAIN], TACZ_TALLY[INJECTED_ALL],
                TACZ_TALLY[SAW_FRAGMENT]);
        // HAND_CUTOUT 每局必存在（scope 管线每局都钉上去）；完整管线（≥20 程序）
        // 却 0 hand 注入 = 命名约定变了，过滤器全漏 —— 大声报出来，别静默。
        if (!tacz$warnedNoHand && TACZ_TALLY[SAW_FRAGMENT] >= 20 && TACZ_TALLY[INJECTED_HAND] == 0
                && IrisScopeMaskState.injectionPolicy() == RenderConfig.IrisScopeMaskInjection.HAND_ONLY) {
            tacz$warnedNoHand = true;
            GunMod.LOGGER.warn("[TACZ Scope] Intercepted {} fragment shaders but patched 0 HAND programs; "
                    + "the 'hand' name filter missed everything (Iris naming changed?). "
                    + "Scope clipping is probably broken; please report this line.", TACZ_TALLY[SAW_FRAGMENT]);
        }
    }

    /**
     * 在源码里找「真正的」{@code void main() {}} 入口（main 起点 + 函数体左花括号）。
     *
     * <p>老实现是 {@code indexOf("void main")} 取首个 —— 落进注释（pack 头部注释里出现
     * void main 字样）或撞上 {@code avoid maintain} 这类子串时，声明会被插进注释里
     * （无声失效）或声明与分支分家（未声明标识符 → 整个程序编译失败 → Iris 静默回退
     * 到无光影 fallback）。这里单遍扫描剔除 {@code //}、块注释与双引号字面量，
     * 再验证「void main(…) {」的完整定义形状；找不到就返回 null，
     * 调用方 fail-closed（原样返回 + 告警），绝不猜位置下刀。
     */
    private static MainSite tacz$findMainSite(String source) {
        int n = source.length();
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n) {
                char d = source.charAt(i + 1);
                if (d == '/') {
                    i = tacz$skipLineComment(source, i + 2);
                    continue;
                }
                if (d == '*') {
                    i = tacz$skipBlockComment(source, i + 2);
                    continue;
                }
            }
            if (c == '"') {
                i = tacz$skipString(source, i + 1);
                continue;
            }
            if (c == 'v' && tacz$matchesVoidMain(source, i)) {
                int brace = tacz$matchMainDefinition(source, i + TACZ_VOID_MAIN.length());
                if (brace >= 0) {
                    return new MainSite(i, brace);
                }
                // 名字对但形状不对（如 void main(); 前置声明）：继续往后找真身。
                i += TACZ_VOID_MAIN.length();
                continue;
            }
            i++;
        }
        return null;
    }

    private static final String TACZ_VOID_MAIN = "void main";

    private record MainSite(int mainStart, int bodyBrace) {
    }

    private static int tacz$skipLineComment(String source, int i) {
        int n = source.length();
        while (i < n && source.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private static int tacz$skipBlockComment(String source, int i) {
        int n = source.length();
        while (i + 1 < n) {
            if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') {
                return i + 2;
            }
            i++;
        }
        return n;
    }

    private static int tacz$skipString(String source, int i) {
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    private static boolean tacz$isWordChar(char c) {
        return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** 字面匹配 + 两侧单词边界（{@code avoid maintain} 这类子串必须拒掉）。 */
    private static boolean tacz$matchesVoidMain(String source, int i) {
        int n = source.length();
        if (i + TACZ_VOID_MAIN.length() > n || !source.startsWith(TACZ_VOID_MAIN, i)) {
            return false;
        }
        if (i > 0 && tacz$isWordChar(source.charAt(i - 1))) {
            return false;
        }
        int end = i + TACZ_VOID_MAIN.length();
        return end >= n || !tacz$isWordChar(source.charAt(end));
    }

    /**
     * 从 {@code void main} 之后验证「( [void] ) {」形状，返回左花括号下标。
     * 括号/花括号之间允许空白与注释（合法 GLSL）。
     */
    private static int tacz$matchMainDefinition(String source, int i) {
        int n = source.length();
        i = tacz$skipTrivia(source, i);
        if (i >= n || source.charAt(i) != '(') {
            return -1;
        }
        i = tacz$skipTrivia(source, i + 1);
        // GLSL 的 main 不取参数；桌面 GLSL 允许 void main(void) 的写法，一并接受。
        if (i + 4 <= n && source.startsWith("void", i)
                && (i == 0 || !tacz$isWordChar(source.charAt(i - 1)))
                && (i + 4 >= n || !tacz$isWordChar(source.charAt(i + 4)))) {
            i = tacz$skipTrivia(source, i + 4);
        }
        if (i >= n || source.charAt(i) != ')') {
            return -1;
        }
        i = tacz$skipTrivia(source, i + 1);
        if (i < n && source.charAt(i) == '{') {
            return i;
        }
        return -1;
    }

    /** 跳过空白与注释（main 签名内部的合法点缀）。 */
    private static int tacz$skipTrivia(String source, int i) {
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f') {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char d = source.charAt(i + 1);
                if (d == '/') {
                    i = tacz$skipLineComment(source, i + 2);
                    continue;
                }
                if (d == '*') {
                    i = tacz$skipBlockComment(source, i + 2);
                    continue;
                }
            }
            return i;
        }
        return i;
    }

    private static String tacz$injectScopeMask(String source, String programName) {
        MainSite site = tacz$findMainSite(source);
        if (site == null) {
            String label = programName == null ? "<unknown>" : programName;
            if (TACZ_WARNED_NO_MAIN.add(label)) {
                GunMod.LOGGER.warn("[TACZ Scope] Could not find a verifiable 'void main() {' in Iris program {}; "
                        + "leaving it unpatched (scope clipping will not apply to this program).", label);
            }
            return source;
        }
        // 【这里只做 discard，不写颜色 —— 别再往回加采样了】
        //
        // 镜内放大由 {@code ScopePipRenderer.compositeAfterLevelUnderShaders()} 在
        // Iris 整条管线跑完之后、直接在最终画面上完成。这里的职责只剩一件：
        // 让镜身在孔径内 discard，于是最终画面里孔径那块是干净的 1× 世界 ——
        // 那正是后续放大所需要的、不含枪的素材。
        //
        // 曾经在这里注入「采样 colortexN 并写颜色」，两次实测都失败：
        //   ① 采 colortex0 → 纯黑（gbuffers_hand 跑在延迟光照之前，没有已着色的场景）；
        //   ② 把枪挪进半透明 pass、再按 pack 的 RENDERTARGETS 采 colortex2 → 灰噪块。
        // 根子上这条路要求猜中别家 pack 的内部缓冲约定，每修一次都是下一次盲猜；
        // 而且它还引入过一次严重回归：为采样补 colortex 声明时前缀撞名，
        // 导致 `undefined variable "colortex1"` → Iris 整体禁用光影 → Voxy 视口全 0。
        // 现在这段注入完全不引用任何 colortex，那类事故从根上消失了。
        //
        // 2026-09 修订：注入体文本保持逐字节不变（HAND 程序行为零变化），
        // 变的只是作用域 —— 世界程序不再经过这里（见类注释）。
        String declarations = "\n// TACZ Iris scope mask bridge: 0=off, 1=body discard-inside, "
                + "2=reticle discard-outside\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ScopeMaskSampler;\n\n";

        String branch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_scopeMaskUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ScopeMaskSampler, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskSample = texture(tacz_ScopeMaskSampler, tacz_scopeMaskUv).rg;\n"
                + "        bool tacz_insideScope = tacz_maskSample.r > 0.5;\n"
                + "        if (tacz_insideScope) {\n"
                + "            float tacz_progress = tacz_maskSample.g;\n"
                + "            if (tacz_progress < 0.999) {\n"
                + "                const int RINGS = 3;\n"
                + "                const int STEPS = 8;\n"
                + "                float inside = 0.0;\n"
                + "                float total = 0.0;\n"
                + "                float unit = 0.055;\n"
                + "                vec2 tacz_texSize = vec2(textureSize(tacz_ScopeMaskSampler, 0));\n"
                + "                for (int r = 1; r <= RINGS; r++) {\n"
                + "                    float radius = unit * float(r) / float(RINGS);\n"
                + "                    for (int i = 0; i < STEPS; i++) {\n"
                + "                        float a = 6.2831853 * float(i) / float(STEPS);\n"
                + "                        vec2 off = vec2(cos(a), sin(a)) * radius;\n"
                + "                        off.x *= tacz_texSize.y / max(tacz_texSize.x, 1.0);\n"
                + "                        total += 1.0;\n"
                + "                        inside += texture(tacz_ScopeMaskSampler, tacz_scopeMaskUv + off).r > 0.5 ? 1.0 : 0.0;\n"
                + "                    }\n"
                + "                }\n"
                + "                float depth = total > 0.0 ? inside / total : 1.0;\n"
                + "                if (depth < 1.0 - tacz_progress) {\n"
                + "                    tacz_insideScope = false;\n"
                + "                }\n"
                + "            }\n"
                + "        }\n"
                + "        if ((tacz_ScopeMaskMode == 1 && tacz_insideScope) || (tacz_ScopeMaskMode == 2 && !tacz_insideScope)) {\n"
                + "            discard;\n"
                + "        }\n"
                + "    }\n";

        return source.substring(0, site.mainStart()) + declarations
                + source.substring(site.mainStart(), site.bodyBrace() + 1) + branch
                + source.substring(site.bodyBrace() + 1);
    }
}
