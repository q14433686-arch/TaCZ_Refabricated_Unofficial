package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.client.RenderConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
 * <p>【HAND 判定从哪来】
 * {@code ShaderCreator.link(name, ...)} 本身只收名字不收 key；但对 Iris 26.2
 * 源码实读确认：全部四条 {@code create*(...)}（{@code create / createShadow /
 * createFallback / createFallbackShadow}）都<b>同步（同线程、无延迟）</b>调用
 * {@code link}，且入口参数里同时带 {@code name} 与 {@code shaderKey}。
 * 于是用两个 HEAD 注入把 (name, keyName) 记进 ThreadLocal 单槽，
 * link 的 ModifyVariable 把它消费掉做判定 —— 同步嵌套保证时序，
 * 不依赖任何 mixin 处理器排序约定。key 按枚举名 {@code HAND} 前缀识别
 * （Iris 各版本最稳的约定；玩家日志里 Iris 自己打的
 * {@code Found ... program match ...: HAND_CUTOUT} 即证），program 名含
 * hand（忽略大小写）作为第二道保险 —— 只会多注、不会少注。
 *
 * <p>【fail-open，不静默断功能】
 * 上下文缺失（未来 Iris 改了 create 签名、两个 HEAD 注入都没装上）→ 按老行为
 * 全量注入 + 一次性告警：宁可回到老路，也不在静默中断瞄具裁剪。
 * 配置 {@code ScopeMaskIrisInjection=false} 可彻底关闭注入做对照实验。
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisShaderCreatorMixin {
    /** create*(...) → link(...) 的同步调用上下文桥（单槽覆盖；link 消费即清）。 */
    private static final ThreadLocal<CreateContext> TACZ_CREATE_CONTEXT = new ThreadLocal<>();

    private record CreateContext(String programName, String shaderKeyName) {
    }

    // create / createShadow 的前三个参数都是 (pipeline, name, shaderKey)。
    // pipeline 与 shaderKey 是 Iris 内部类，本仓库编译时不链接 Iris，
    // 故用 @Coerce Object 接收（与 IrisGlCommandEncoderMixin 同一手法）；
    // name 恒为 String，直接精确匹配。require=0 兜底：未来 Iris 改签名导致
    // 描述符对不上时静默不装，link 侧按无上下文 fail-open 处理。
    @Inject(method = {"create", "createShadow"}, at = @At("HEAD"), require = 0)
    private static void tacz$captureCreateContext(@Coerce Object pipeline, String name,
                                                  @Coerce Object shaderKey, CallbackInfo ci) {
        TACZ_CREATE_CONTEXT.set(new CreateContext(name, tacz$keyName(shaderKey)));
    }

    // createFallback / createFallbackShadow 的前两个参数是 (name, shaderKey)。
    @Inject(method = {"createFallback", "createFallbackShadow"}, at = @At("HEAD"), require = 0)
    private static void tacz$captureFallbackContext(String name, @Coerce Object shaderKey, CallbackInfo ci) {
        TACZ_CREATE_CONTEXT.set(new CreateContext(name, tacz$keyName(shaderKey)));
    }

    private static String tacz$keyName(Object shaderKey) {
        if (shaderKey == null) {
            return null;
        }
        // ShaderKey 是枚举：Enum#name() 不受未来 toString() 改写影响。
        if (shaderKey instanceof Enum<?> e) {
            return e.name();
        }
        return shaderKey.toString();
    }

    @ModifyVariable(
            method = "link",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5,
            require = 0
    )
    private static String tacz$patchLinkedFragment(String source) {
        CreateContext ctx = TACZ_CREATE_CONTEXT.get();
        // 消费语义：每次 link 只用最新的一份；create 中途抛异常没走到 link 时，
        // 残留也会被下一次 create 入口覆盖，不会错配给别人的 link。
        TACZ_CREATE_CONTEXT.remove();
        if (source == null) {
            return null;
        }
        String programName = ctx == null ? null : ctx.programName();
        String keyName = ctx == null ? null : ctx.shaderKeyName();

        if (!tacz$injectionEnabled()) {
            tacz$tally(SKIP_CONFIG);
            tacz$logDecision(programName, keyName, "skipped (ScopeMaskIrisInjection=false)");
            tacz$maybeSummarize();
            return source;
        }
        if (source.contains("tacz_ScopeMaskMode")) {
            // 幂等：同一份源码第二次过 link（正常流程不应发生，防未来 Iris 改流程）。
            tacz$logDecision(programName, keyName, "skipped (already contains the bridge)");
            tacz$maybeSummarize();
            return source;
        }
        if (ctx == null) {
            // 未知调用路径：按老行为注入，保证瞄具裁剪不断；一次性告警让人来修过滤器。
            if (!tacz$warnedNoContext) {
                tacz$warnedNoContext = true;
                GunMod.LOGGER.warn("[TACZ Scope] An Iris program reached link() without a TACZ create-context "
                        + "(Iris internals changed?). Injecting unconditionally as before; if the world turns "
                        + "transparent under shaders, please report this line.");
            }
            tacz$tally(INJECTED_FALLBACK);
            tacz$logDecision(null, null, "INJECTED (no create-context; fail-open)");
            String patched = tacz$injectScopeMask(source, null);
            tacz$maybeSummarize();
            return patched;
        }
        if (!tacz$isHandProgram(keyName, programName)) {
            tacz$tally(SKIP_WORLD);
            tacz$logDecision(programName, keyName, "skipped (not a HAND program; left byte-identical)");
            tacz$maybeSummarize();
            return source;
        }
        String patched = tacz$injectScopeMask(source, programName);
        if (patched == source) {
            tacz$tally(SKIP_NO_MAIN);
        } else {
            tacz$tally(INJECTED_HAND);
        }
        tacz$logDecision(programName, keyName, patched == source
                ? "skipped (no verifiable 'void main() {' found; fail-closed)"
                : "INJECTED (HAND program)");
        tacz$maybeSummarize();
        return patched;
    }

    /**
     * HAND 判定：key 枚举名 {@code HAND} 前缀为主，program 名含 hand 为保险。
     *
     * <p>第二道是保守方向：名字像 hand 就注 —— 多注一个世界程序只是回到老行为，
     * 少注一个 hand 程序却会静默杀掉瞄具裁剪。两者取并集。
     */
    private static boolean tacz$isHandProgram(String keyName, String programName) {
        if (keyName != null && keyName.startsWith("HAND")) {
            return true;
        }
        return programName != null && programName.toLowerCase(Locale.ROOT).contains("hand");
    }

    /** 诊断总开关。配置类没就绪/读失败一律按开处理（= 当前行为，不静默断功能）。 */
    private static boolean tacz$injectionEnabled() {
        try {
            return RenderConfig.SCOPE_MASK_IRIS_INJECTION == null
                    || RenderConfig.SCOPE_MASK_IRIS_INJECTION.get();
        } catch (Throwable t) {
            return true;
        }
    }

    private static final int INJECTED_HAND = 0;
    private static final int SKIP_WORLD = 1;
    private static final int SKIP_CONFIG = 2;
    private static final int SKIP_NO_MAIN = 3;
    private static final int INJECTED_FALLBACK = 4;
    /** 管线构建只发生在渲染线程；计数纯诊断，近似即可。 */
    private static final long[] TACZ_TALLY = new long[5];
    private static boolean tacz$warnedNoContext;
    private static long tacz$lastSummaryMs;
    /** 每个 program 名只告警一次（名是有限集，跨重建复用，不会无界增长）。 */
    private static final Set<String> TACZ_WARNED_NO_MAIN = Collections.synchronizedSet(new HashSet<>());

    private static void tacz$tally(int kind) {
        TACZ_TALLY[kind]++;
    }

    private static void tacz$logDecision(String programName, String keyName, String decision) {
        if (GunMod.LOGGER.isDebugEnabled()) {
            GunMod.LOGGER.debug("[TACZ Scope] Iris program link: name={} key={} -> {}",
                    programName, keyName, decision);
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
                        + "({} config-skipped, {} no-verifiable-main skipped, {} fail-open injected without context).",
                TACZ_TALLY[INJECTED_HAND], TACZ_TALLY[SKIP_WORLD],
                TACZ_TALLY[SKIP_CONFIG], TACZ_TALLY[SKIP_NO_MAIN], TACZ_TALLY[INJECTED_FALLBACK]);
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
