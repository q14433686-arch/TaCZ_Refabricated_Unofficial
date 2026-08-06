package com.tacz.guns.client.render.scope;

import com.tacz.guns.GunMod;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source-level patcher for the Iris shader programs the scope batches are routed through.
 *
 * <h2>为什么这个类必须存在，以及为什么它必须「只改手部 program」</h2>
 *
 * <p>Iris 会用 shader pack 的 {@code gbuffers_hand} / {@code gbuffers_hand_water} program 顶替我们
 * 注册的 {@code scope_depth_aperture} / {@code scope_depth_cleanup} / {@code scope_*_reticle} 管线
 * （{@code IrisApi#assignPipeline} → {@code ShaderKey.HAND_CUTOUT / HAND_TRANSLUCENT}）。
 * 也就是说在 Iris 下我们<b>拿不到自己的 fragment shader</b>，深度还原分支和目镜遮罩分支
 * 只能注入到 pack 的手部 program 里。
 *
 * <p>旧实现（{@code IrisDepthRestoreShaderMixin}）却把这两段分支注入到了
 * <b>Iris 链接的每一个 fragment shader</b>：gbuffers 的 terrain / entities / block entities /
 * particles / weather / clouds / sky / basic / lines，以及全部 shadow program 和 fallback program。
 * 这带来四类真实缺陷，而且每一类都是「同一个包、换台机器表现不同」的未定义行为 ——
 * 正是本次「只有开 Complementary 才炸、且每台机器炸得不一样」的成因来源。
 *
 * <ol>
 *   <li><b>{@code gl_FragDepth} 污染（最严重）。</b>GLSL 规定：只要 fragment shader
 *       <i>静态地</i>给 {@code gl_FragDepth} 赋过值，那么任何没有给它赋值的执行路径上，
 *       该片元的深度就是<b>未定义</b>的（GLSL 4.60 §7.1.4 / GLSL 3.30 §7.2）。旧注入让
 *       <i>所有</i> program 都静态写 {@code gl_FragDepth}，而 99.99% 的 draw 都走
 *       {@code tacz_DepthRestoreMode == 0} 这条<i>不写</i>的路径。部分驱动（尤其 NVIDIA）
 *       会把它隐式初始化成 {@code gl_FragCoord.z}，于是看不出问题；AMD / Mesa / Intel 不保证，
 *       RDNA 上更会直接切到 depth-export 模式并导出一个从未写过的寄存器 ——
 *       世界几何、实体、粒子甚至 shadow map 的深度全部被随机值污染。
 *       附带代价：静态写 {@code gl_FragDepth} 会让驱动<b>关闭 early-Z / HiZ</b>，
 *       整个世界的所有 pass 都失去提前深度剔除。
 *       实测佐证：Complementary（Reimagined / Unbound r5.8.1 同源）<b>整包 0 处</b>
 *       使用 {@code gl_FragDepth}；Photon 唯一一处在 {@code gbuffers_all_solid.fsh}
 *       且被 {@code PROGRAM_GBUFFERS_VOXELS} 挡住。也就是说这条静态赋值是我们
 *       <i>凭空塞进去</i>的，pack 自己完全没有 depth-export 的语义。</li>
 *   <li><b>声明插在 {@code #version} 与 {@code #extension} 之间。</b>Iris 的 jcpp
 *       预处理器会把 pack 里所有 {@code #extension} 提升到文件最顶部紧跟 {@code #version}
 *       （{@code JcppProcessor} + {@code GlslCollectingListener}），glsl-transformer 打印
 *       AST 时同样把 extension 放在所有声明之前 —— 这也是 Iris 自己只用
 *       {@code ASTInjectionPoint.BEFORE_DECLARATIONS}（在 extension 之后）的原因，
 *       见 {@code StringTransformations} 里那段「We need to avoid injecting non-preprocessor
 *       code fragments before #extension declarations」的注释。旧实现按
 *       「{@code #version} 行末尾」插入，正好落在 extension 前面。GLSL 要求
 *       {@code #extension} 必须出现在任何非预处理 token 之前：Mesa / AMD 直接编译报错，
 *       NVIDIA 只给 warning。同一个 pack、同样的选项，换台机器就是不同结果。</li>
 *   <li><b>采样器单元膨胀。</b>被注入的 {@code depthtex2} 会变成每个 program 的 active
 *       uniform，于是 Iris 的 {@code ProgramSamplers.Builder} 会为<i>每一个</i> gbuffer/shadow
 *       program 真的分配一个纹理单元（Complementary 自己<b>从不声明 depthtex2</b>，
 *       所以这是净 +1）；一旦某个 program 越过 {@code GL_MAX_TEXTURE_IMAGE_UNITS}
 *       （Intel 核显只有 16），Iris 会抛
 *       {@code IllegalStateException("No more available texture units while activating sampler …")}，
 *       整个 pipeline 创建失败。到底越不越界既取决于 pack，也取决于用户在本机勾了哪些
 *       pack 选项（日志里的 {@code Profile: Custom (+N options changed by user)}）——
 *       这正是「每台机器不一样」的第二个来源。</li>
 *   <li><b>非一致 {@code discard} 破坏求导。</b>旧遮罩分支把 {@code discard} 放在 main()
 *       <i>开头</i>，而它的条件是逐像素的深度比较。同一个 2x2 quad 内的 lane 会非一致地
 *       退出，之后 pack 自己所有隐式 LOD 采样与 {@code dFdx/dFdy/fwidth} 的结果在 AMD 上
 *       就是垃圾。Complementary 的 {@code gbuffers_hand} 里恰好带着一行
 *       {@code alphaCheck = max(fwidth(color.a), alphaCheck); // Fixes artifacts on fragment
 *       edges with non-nvidia gpus}，以及 {@code ComputeTexelOffset()} 里的
 *       {@code dFdx/dFdy}。注意 Iris 自己追加的 alpha test 也是
 *       {@code appendMainFunctionBody(...)}，即放在 main() <b>末尾</b> —— 与本类的做法一致。</li>
 * </ol>
 *
 * <p>因此这里做五件事：
 * <ul>
 *   <li>只给手部 program 注入。Iris 的 program 名就是
 *       {@code ShaderKey#getName()}（枚举名小写），全部手部 key 都以 {@code hand} 开头
 *       （{@code hand_cutout}、{@code hand_cutout_bright}、{@code hand_cutout_diffuse}、
 *       {@code hand_text}、{@code hand_text_translucent}、{@code hand_text_intensity}、
 *       {@code hand_translucent}、{@code hand_water_bright}、{@code hand_water_diffuse}），
 *       且没有任何非手部 key 以 {@code hand} 开头；</li>
 *   <li>声明插到<b>整段预处理序言之后</b>（跳过 {@code #version}/{@code #extension}/
 *       {@code #pragma}/注释/空行），等价于 Iris 自己的 {@code BEFORE_DECLARATIONS}；</li>
 *   <li>在 main() 开头无条件写一次 {@code gl_FragDepth = gl_FragCoord.z;}，
 *       让「静态赋值」在所有路径上都成为「实际赋值」，消灭未定义深度；代价只落在
 *       手部这几个 program 上（第一人称手臂/枪械只覆盖屏幕一小块），世界与阴影 pass
 *       完全不再被牵连；</li>
 *   <li>遮罩的 {@code discard} 放到 main() <b>结尾</b>，让 pack 自己的求导全部在一致
 *       控制流里完成；我们自己的深度取样一律用 {@code textureLod(..., 0.0)}，不产生
 *       隐式求导；</li>
 *   <li>任何我们无法自信解析的源码（没有 {@code #version}、版本低于 130、已有
 *       {@code layout(depth_*)} 限定符、找不到 main()）一律原样返回 —— 宁可 Iris 下
 *       没有目镜遮罩，也不能生成未定义行为的 shader。</li>
 * </ul>
 */
public final class ScopeShaderInjector {
    /** Idempotency marker; also the uniform {@link ScopeDepthCopyState} looks up. */
    private static final String MARKER = ScopeDepthCopyState.MASK_MODE_UNIFORM;

    /** Iris program names for the first-person hand pass all start with this. */
    private static final String HAND_PROGRAM_PREFIX = "hand";

    /**
     * {@code textureSize} and {@code textureLod} in a fragment shader need GLSL 1.30. Iris raises
     * every pack below 330 to {@code #version 330 core}, so this should always hold; the guard just
     * makes sure a future Iris that stops doing that degrades to "no mask" instead of "won't compile".
     */
    private static final int MIN_GLSL_VERSION = 130;

    private static final Pattern MAIN_PATTERN =
            Pattern.compile("(?<![0-9A-Za-z_])void\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("#version\\s+(\\d+)");

    /**
     * Program name Iris is about to link, captured from {@code ShaderPrinter#printProgram(String)},
     * which every {@code ShaderCreator} path (create / createFallback / createShadow /
     * createFallbackShadow) calls immediately before {@code ShaderCreator#link}.
     * Render-thread only, so a plain field is enough.
     */
    private static @Nullable String pendingProgramName;

    private static boolean loggedPatch;
    private static boolean loggedMissingName;
    private static boolean loggedUnparsable;

    private ScopeShaderInjector() {
    }

    /**
     * Called from the {@code ShaderPrinter#resetPrintState} mixin, which Iris runs once per
     * rendering-pipeline creation ({@code IrisRenderingPipeline}'s constructor), i.e. every time a
     * shader pack is loaded, reloaded, or the dimension changes.
     *
     * <p>Drops any name left over from a pipeline that never finished linking, and re-arms the
     * one-shot log lines so every pack load states, in the user's log, whether the scope branches
     * went in and into which program. That is exactly the evidence needed to tell "the mask is off
     * with this pack" apart from "the mod patched something it should not have".
     */
    public static void resetForNewPipeline() {
        pendingProgramName = null;
        loggedPatch = false;
        loggedMissingName = false;
        loggedUnparsable = false;
    }

    /** Called from the {@code ShaderPrinter} mixin right before Iris links the program. */
    public static void setPendingProgramName(@Nullable String name) {
        pendingProgramName = name;
    }

    /**
     * Called from the {@code ShaderCreator#link} mixin. Consumes the pending program name so a
     * stale one can never leak into a later link that we failed to observe.
     */
    public static String patchLinkedFragment(@Nullable String source) {
        String name = pendingProgramName;
        pendingProgramName = null;
        if (name == null) {
            if (source != null && !loggedMissingName) {
                loggedMissingName = true;
                GunMod.LOGGER.warn("[TACZ Scope] Cannot identify the Iris program being linked, so the "
                        + "scope ocular mask stays disabled under shader packs. This means Iris' internals "
                        + "changed: ShaderPrinter#printProgram is no longer called before ShaderCreator#link.");
            }
            return source;
        }
        return patchFragment(name, source);
    }

    /**
     * @return {@code true} when the Iris program is one of the first-person hand programs the scope
     * batches are routed to. Every {@code ShaderKey} whose lowercase name starts with {@code hand}
     * belongs to the hand pass, and no other key does.
     */
    static boolean isHandProgram(@Nullable String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(HAND_PROGRAM_PREFIX);
    }

    /**
     * Injects the dormant depth-restore and ocular-mask branches into an Iris hand fragment shader.
     * Any other program, and any source we cannot parse confidently, is returned untouched.
     */
    static String patchFragment(@Nullable String name, @Nullable String source) {
        if (source == null || source.isEmpty() || !isHandProgram(name) || source.contains(MARKER)) {
            return source;
        }

        String masked = maskCommentsAndStrings(source);

        int version = glslVersion(masked);
        if (version < MIN_GLSL_VERSION) {
            return warnUnparsable(name, "its GLSL version (" + version + ") is below "
                    + MIN_GLSL_VERSION, source);
        }
        // A pack that declares a depth layout qualifier has promised the driver something about
        // gl_FragDepth (depth_unchanged / depth_greater / depth_less). Overwriting it would be UB.
        if (masked.contains("depth_any") || masked.contains("depth_greater")
                || masked.contains("depth_less") || masked.contains("depth_unchanged")) {
            return warnUnparsable(name, "it already constrains gl_FragDepth with a layout qualifier",
                    source);
        }

        Matcher matcher = MAIN_PATTERN.matcher(masked);
        if (!matcher.find()) {
            return warnUnparsable(name, "it has no parsable main()", source);
        }
        int openBrace = matcher.end() - 1;
        int closeBrace = matchClosingBrace(masked, openBrace);
        if (closeBrace < 0) {
            return warnUnparsable(name, "its main() has unbalanced braces", source);
        }

        int declarationPos = afterPreprocessorPrologue(masked);
        if (declarationPos > matcher.start()) {
            // Defensive: a shader whose whole prologue reads as preprocessor text.
            return warnUnparsable(name, "its preprocessor prologue overlaps main()", source);
        }

        String declarations = buildDeclarations(declaresIdentifier(masked, source, "depthtex2"));
        String prologue = buildMainPrologue();
        String epilogue = buildMainEpilogue();

        StringBuilder patched = new StringBuilder(
                source.length() + declarations.length() + prologue.length() + epilogue.length());
        patched.append(source, 0, declarationPos)
                .append(declarations)
                .append(source, declarationPos, openBrace + 1)
                .append(prologue)
                .append(source, openBrace + 1, closeBrace)
                .append(epilogue)
                .append(source, closeBrace, source.length());

        if (!loggedPatch) {
            loggedPatch = true;
            GunMod.LOGGER.info("[TACZ Scope] Injected the dormant depth-restore and ocular-mask branches "
                    + "into the Iris hand programs only (first: {}). World, entity, particle and shadow "
                    + "programs are left untouched.", name);
        }
        return patched.toString();
    }

    private static String warnUnparsable(String name, String reason, String source) {
        if (!loggedUnparsable) {
            loggedUnparsable = true;
            GunMod.LOGGER.warn("[TACZ Scope] Not patching the Iris {} fragment shader because {}; "
                    + "the scope ocular mask stays disabled for this shader pack.", name, reason);
        }
        return source;
    }

    private static String buildDeclarations(boolean declaresDepthTex2) {
        // Iris copies the exact world depth immediately before HAND_SOLID and publishes it as
        // depthtex2 (IrisRenderingPipeline#beginHand -> RenderTargets#copyPreHandDepth), and
        // IrisSamplers#addWorldDepthSamplers offers that name to every gbuffer program. Reuse it
        // rather than blitting the currently bound hand FBO, whose depth can start cleared.
        return "\n// ---- TACZ ocular scope branches (hand programs only; dormant for ordinary draws) ----\n"
                + "uniform int " + ScopeDepthCopyState.MODE_UNIFORM + ";\n"
                + "uniform int " + MARKER + ";\n"
                + "uniform sampler2D " + ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM + ";\n"
                + (declaresDepthTex2
                        ? ""
                        : "uniform sampler2D " + ScopeDepthCopyState.IRIS_WORLD_DEPTH_UNIFORM + ";\n")
                + "// ---- end TACZ scope declarations ----\n";
    }

    private static String buildMainPrologue() {
        // 1) gl_FragDepth is written on EVERY path. Once a fragment shader statically assigns it,
        //    GLSL leaves the depth undefined on the paths that do not - which would be every
        //    ordinary hand draw. Seeding it with gl_FragCoord.z reproduces fixed-function depth
        //    exactly, so this is a no-op for the pack while making the assignment well-defined.
        // 2) Both branch conditions are uniforms, so this block never breaks uniform control flow.
        // 3) textureLod(..., 0.0) keeps our own depth fetches free of implicit derivatives.
        // 4) The restore branch returns before the pack's shading AND before Iris' appended alpha
        //    test: the cleanup quad must write depth unconditionally, and its pipeline already has
        //    ColorTargetState.WRITE_NONE so the unwritten colour outputs can never reach a buffer.
        return "\n    // ---- TACZ scope: dormant unless ScopeDepthCopyState enables a mode ----\n"
                + "    bool tacz_scopeMaskDiscard = false;\n"
                + "    gl_FragDepth = gl_FragCoord.z;\n"
                + "    if (" + ScopeDepthCopyState.MODE_UNIFORM + " != 0) {\n"
                + "        vec2 tacz_restoreUv = gl_FragCoord.xy / max(vec2(textureSize("
                + ScopeDepthCopyState.IRIS_WORLD_DEPTH_UNIFORM + ", 0)), vec2(1.0));\n"
                + "        gl_FragDepth = textureLod("
                + ScopeDepthCopyState.IRIS_WORLD_DEPTH_UNIFORM + ", tacz_restoreUv, 0.0).r;\n"
                + "        return;\n"
                + "    }\n"
                + "    if (" + MARKER + " != 0) {\n"
                + "        vec2 tacz_maskWorldUv = gl_FragCoord.xy / max(vec2(textureSize("
                + ScopeDepthCopyState.IRIS_WORLD_DEPTH_UNIFORM + ", 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskApertureUv = gl_FragCoord.xy / max(vec2(textureSize("
                + ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM + ", 0)), vec2(1.0));\n"
                + "        float tacz_maskWorldDepth = textureLod("
                + ScopeDepthCopyState.IRIS_WORLD_DEPTH_UNIFORM + ", tacz_maskWorldUv, 0.0).r;\n"
                + "        float tacz_maskApertureDepth = textureLod("
                + ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM + ", tacz_maskApertureUv, 0.0).r;\n"
                + "        tacz_scopeMaskDiscard = !(tacz_maskApertureDepth < tacz_maskWorldDepth - 1.0e-6);\n"
                + "    }\n"
                + "    // ---- end TACZ scope prologue ----\n";
    }

    private static String buildMainEpilogue() {
        // Deliberately the LAST statement of main(), after the pack's shading and after Iris' own
        // appended alpha test (CommonTransformer#appendMainFunctionBody). A reticle pixel outside
        // the ocular footprint is still shaded (cheap - a reticle is a few hundred pixels) but
        // never reaches the framebuffer: discard suppresses colour AND depth writes wherever it
        // appears. Discarding at the top instead would kill lanes non-uniformly inside a 2x2 quad
        // and leave every later dFdx/dFdy/fwidth and implicit-LOD fetch undefined on AMD.
        return "\n    // ---- TACZ scope: ocular mask, last so the pack's derivatives stay uniform ----\n"
                + "    if (tacz_scopeMaskDiscard) { discard; }\n";
    }

    /** @return the {@code #version} number of the source, or -1 when there is none. */
    static int glslVersion(String masked) {
        Matcher matcher = VERSION_PATTERN.matcher(masked);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return the offset just past {@code #version}, the hoisted {@code #extension}/{@code #pragma}
     * directives, and any leading comments or blank lines. GLSL rejects an {@code #extension} that
     * follows a real declaration, so nothing may be inserted before this point. This is the string
     * equivalent of Iris' own {@code ASTInjectionPoint.BEFORE_DECLARATIONS}.
     */
    static int afterPreprocessorPrologue(String masked) {
        int length = masked.length();
        int index = 0;
        while (index < length) {
            int lineEnd = masked.indexOf('\n', index);
            int limit = lineEnd < 0 ? length : lineEnd;
            String line = masked.substring(index, limit).trim();
            if (!line.isEmpty() && line.charAt(0) != '#') {
                return index;
            }
            if (lineEnd < 0) {
                return length;
            }
            index = lineEnd + 1;
            // A preprocessor directive may be continued with a trailing backslash.
            while (line.endsWith("\\") && index < length) {
                int nextEnd = masked.indexOf('\n', index);
                int nextLimit = nextEnd < 0 ? length : nextEnd;
                line = masked.substring(index, nextLimit).trim();
                index = nextEnd < 0 ? length : nextEnd + 1;
            }
        }
        return length;
    }

    /** @return index of the {@code }} matching {@code masked.charAt(openBrace)}, or -1. */
    static int matchClosingBrace(String masked, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Replaces every comment and string body with spaces while keeping the exact length, so index
     * arithmetic on the result maps 1:1 onto the original source. Prevents a {@code void main} or a
     * brace inside a comment (Complementary's block-comment {@code DRAWBUFFERS} markers, for
     * instance) from steering the injection.
     */
    static String maskCommentsAndStrings(String source) {
        char[] out = source.toCharArray();
        int length = out.length;
        int i = 0;
        while (i < length) {
            char c = out[i];
            if (c == '/' && i + 1 < length && out[i + 1] == '/') {
                while (i < length && out[i] != '\n') {
                    out[i++] = ' ';
                }
            } else if (c == '/' && i + 1 < length && out[i + 1] == '*') {
                out[i++] = ' ';
                out[i++] = ' ';
                while (i < length) {
                    if (out[i] == '*' && i + 1 < length && out[i + 1] == '/') {
                        out[i++] = ' ';
                        out[i++] = ' ';
                        break;
                    }
                    if (out[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
            } else if (c == '"') {
                out[i++] = ' ';
                while (i < length && out[i] != '"' && out[i] != '\n') {
                    out[i++] = ' ';
                }
                if (i < length && out[i] == '"') {
                    out[i++] = ' ';
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** @return whether {@code identifier} appears in real code (not only inside a comment). */
    static boolean declaresIdentifier(String masked, String source, String identifier) {
        int from = 0;
        while (true) {
            int at = masked.indexOf(identifier, from);
            if (at < 0) {
                return false;
            }
            boolean startOk = at == 0 || !isIdentifierChar(source.charAt(at - 1));
            int after = at + identifier.length();
            boolean endOk = after >= source.length() || !isIdentifierChar(source.charAt(after));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isIdentifierChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }
}
