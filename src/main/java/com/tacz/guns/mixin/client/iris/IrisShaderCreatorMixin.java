package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Injects a dormant scope mask branch into Iris linked fragment shaders.
 *
 * <p>When Iris is active, it replaces vanilla shaders (including our custom scope_body shader)
 * with its own ExtendedShader programs. This mixin patches the GLSL source of those programs
 * to include the mask sampling logic.</p>
 *
 * <h2>What gets injected</h2>
 * <ul>
 *   <li>Uniform declarations: {@code tacz_ScopeMaskMode} (int) and {@code ScopeMaskSampler} (sampler2D)</li>
 *   <li>A conditional branch at the top of main() that samples the mask and discards pixels</li>
 *   <li>Mode 0 = dormant (no masking), mode 1 = body (discard inside), mode 2 = reticle (discard outside)</li>
 *   <li>Edge softening: ring-based sampling around the mask boundary for smooth ADS transitions</li>
 * </ul>
 *
 * <p>The uniforms are set per-draw by {@link com.tacz.guns.compat.iris.IrisScopeMaskState}
 * via the {@code GlCommandEncoder#trySetup} hook.</p>
 *
 * <h2>Key design choices</h2>
 * <ul>
 *   <li>Uses the same uniform names as the vanilla path's scope_body.fsh for consistency</li>
 *   <li>The mask is RGBA8 (not depth): R=inside ocular, G=aiming progress</li>
 *   <li>No depth buffer manipulation — downstream effects (fog, SSAO) are unaffected</li>
 *   <li>The restore branch from the old approach is completely removed</li>
 * </ul>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisShaderCreatorMixin {
    private static boolean tacz$loggedPatch;

    @ModifyVariable(
            method = "link",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5,
            require = 0
    )
    private static String tacz$patchLinkedFragment(String source) {
        if (source == null) return null;
        String patched = tacz$injectScopeMask(source);
        if (patched != source && !tacz$loggedPatch) {
            tacz$loggedPatch = true;
            GunMod.LOGGER.info("[TACZ Scope] Injected dormant scope-mask branch into Iris shaders.");
        }
        return patched;
    }

    private static String tacz$injectScopeMask(String source) {
        if (source.contains("tacz_ScopeMaskMode")) return source;

        int main = source.indexOf("void main");
        if (main < 0) return source;
        int brace = source.indexOf('{', main);
        if (brace < 0) return source;

        // Add declarations after #version line (or at the start)
        int declPos = 0;
        if (source.startsWith("#version")) {
            int lineEnd = source.indexOf('\n');
            if (lineEnd >= 0) declPos = lineEnd + 1;
        }

        // Check if ScreenSize is already declared (some Iris shader packs have it)
        boolean hasScreenSize = source.contains("ScreenSize");

        String declarations = "\n// TACZ scope mask bridge: 0=off, 1=body discard-inside, 2=reticle discard-outside\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D ScopeMaskSampler;\n"
                + (hasScreenSize ? "" : "uniform vec2 ScreenSize;\n")
                + "\n";

        // The mask branch with ring-based edge softening for ADS transitions
        String branch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_maskUv = gl_FragCoord.xy / max(vec2(textureSize(ScopeMaskSampler, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskSample = texture(ScopeMaskSampler, tacz_maskUv).rg;\n"
                + "        bool tacz_insideScope = tacz_maskSample.r > 0.5;\n"
                + "        if (tacz_insideScope) {\n"
                + "            float tacz_progress = tacz_maskSample.g;\n"
                + "            if (tacz_progress < 0.999) {\n"
                + "                const int RINGS = 3;\n"
                + "                const int STEPS = 8;\n"
                + "                float inside = 0.0;\n"
                + "                float total = 0.0;\n"
                + "                float unit = 0.055;\n"
                + "                vec2 tacz_texSize = vec2(textureSize(ScopeMaskSampler, 0));\n"
                + "                for (int r = 1; r <= RINGS; r++) {\n"
                + "                    float radius = unit * float(r) / float(RINGS);\n"
                + "                    for (int i = 0; i < STEPS; i++) {\n"
                + "                        float a = 6.2831853 * float(i) / float(STEPS);\n"
                + "                        vec2 off = vec2(cos(a), sin(a)) * radius;\n"
                + "                        off.x *= tacz_texSize.y / max(tacz_texSize.x, 1.0);\n"
                + "                        total += 1.0;\n"
                + "                        inside += texture(ScopeMaskSampler, tacz_maskUv + off).r > 0.5 ? 1.0 : 0.0;\n"
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

        // Insert declarations
        String withDecls = source.substring(0, declPos) + declarations + source.substring(declPos);
        int adjustedBrace = brace + declarations.length();

        // Insert branch right after the opening brace of main()
        return withDecls.substring(0, adjustedBrace + 1)
                + branch
                + withDecls.substring(adjustedBrace + 1);
    }
}
