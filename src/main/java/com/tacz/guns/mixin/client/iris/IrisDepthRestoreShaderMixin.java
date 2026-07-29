package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds dormant depth-restore and ocular screen-space mask branches to Iris linked fragment shaders.
 * Both branches are compiled into every shader but stay inert while the mode uniforms are 0;
 * ScopeDepthCopyState enables exactly one of them around the cleanup or reticle draw.
 *
 * <p>Under Iris the mask world-depth source is {@code depthtex2}, which Iris copies immediately
 * before HAND_SOLID, while the aperture depth is the mod-owned copy bound to a high texture unit
 * for the duration of the reticle draw.
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisDepthRestoreShaderMixin {
    private static boolean tacz$loggedPatch;

    @ModifyVariable(
            method = "link",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5,
            require = 0
    )
    private static String tacz$injectScopeBranches(String source) {
        if (source == null || source.contains("tacz_ScopeMaskMode")) {
            return source;
        }
        int main = source.indexOf("void main");
        if (main < 0) {
            return source;
        }
        int brace = source.indexOf('{', main);
        if (brace < 0) {
            return source;
        }

        int declarationPos = 0;
        if (source.startsWith("#version")) {
            int lineEnd = source.indexOf('\n');
            if (lineEnd >= 0) {
                declarationPos = lineEnd + 1;
            }
        }
        // Iris copies world depth immediately before HAND_SOLID and publishes it as depthtex2. Reuse that
        // canonical sampler rather than copying the currently-bound hand FBO, whose depth can start cleared.
        String depthtex2Declaration = source.contains("depthtex2")
                ? ""
                : "uniform sampler2D depthtex2;\n";
        String declarations = "\n// TACZ ocular scope branches; dormant for every ordinary draw\n"
                + "uniform int tacz_DepthRestoreMode;\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ApertureDepthSampler;\n"
                + depthtex2Declaration;
        String restoreBranch = "\n    if (tacz_DepthRestoreMode != 0) {\n"
                + "        vec2 tacz_depthSize = max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_depthUv = gl_FragCoord.xy / tacz_depthSize;\n"
                + "        gl_FragDepth = texture(depthtex2, tacz_depthUv).r;\n"
                + "        return;\n"
                + "    }\n";
        // Reticle pixels survive only inside the ocular footprint: the aperture copy holds the
        // invisible near depth the ocular wrote, so nearer-than-world means "ocular was here".
        String maskBranch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_maskWorldUv = gl_FragCoord.xy / max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskApertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));\n"
                + "        float tacz_maskWorldDepth = texture(depthtex2, tacz_maskWorldUv).r;\n"
                + "        float tacz_maskApertureDepth = texture(tacz_ApertureDepthSampler, tacz_maskApertureUv).r;\n"
                + "        if (!(tacz_maskApertureDepth < tacz_maskWorldDepth - 1.0e-6)) {\n"
                + "            discard;\n"
                + "        }\n"
                + "    }\n";

        String withDeclarations = source.substring(0, declarationPos)
                + declarations + source.substring(declarationPos);
        int adjustedBrace = brace + declarations.length();
        String patched = withDeclarations.substring(0, adjustedBrace + 1)
                + restoreBranch + maskBranch + withDeclarations.substring(adjustedBrace + 1);
        if (!tacz$loggedPatch) {
            tacz$loggedPatch = true;
            GunMod.LOGGER.info("[TACZ Scope] Injected dormant depth-restore and ocular-mask branches into Iris shaders.");
        }
        return patched;
    }
}
