package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Adds depth-restore and ocular screen-space mask branches to Iris hand fragment shaders.
 *
 * <p>Important NVIDIA compatibility note: older revisions injected these dormant branches into
 * every Iris shader-pack program. Even with both mode uniforms at 0, merely adding conditional
 * {@code discard} / {@code gl_FragDepth} paths can make some NVIDIA drivers compile ordinary
 * gbuffers programs differently, producing translucent-looking mobs, arms and gun shells. The
 * scope depth backup is only consumed by first-person hand draws, so this mixin now patches only
 * Iris' first-person hand shader keys/programs.</p>
 *
 * <p>Under Iris the mask world-depth source is {@code depthtex2}, which Iris copies immediately
 * before HAND_SOLID, while the aperture depth is the mod-owned copy bound to a high texture unit
 * for the duration of the reticle draw.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisDepthRestoreShaderMixin {
    private static boolean tacz$loggedPatch;

    @ModifyArgs(
            method = "link",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;createShader(Ljava/lang/String;Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;)I",
                    ordinal = 4
            ),
            require = 0
    )
    private static void tacz$injectScopeBranchesIntoFragmentShader(Args args) {
        String name = (String) args.get(0);
        String source = (String) args.get(2);
        args.set(2, tacz$patchHandFragmentShader(name, source));
    }

    private static String tacz$patchHandFragmentShader(String name, String source) {
        if (!tacz$isHandProgram(name) || source == null || source.contains("tacz_ScopeMaskMode")) {
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
        String declarations = "\n// TACZ ocular scope branches; dormant for ordinary hand draws\n"
                + "uniform int tacz_DepthRestoreMode;\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ApertureDepthSampler;\n"
                + "uniform sampler2D tacz_PostBodyDepthSampler;\n"
                + depthtex2Declaration;
        // Once a shader statically writes gl_FragDepth anywhere, OpenGL leaves the value undefined
        // on paths that do not write it. NVIDIA exposes this aggressively: ordinary hand draws can
        // poison the hand depth buffer, which then breaks water/fog/particles and produces odd lens
        // clipping. Preserve vanilla depth for every normal path; the cleanup branch overwrites it
        // with the sampled pre-hand world depth and returns.
        String restoreBranch = "\n    gl_FragDepth = gl_FragCoord.z;\n"
                + "    if (tacz_DepthRestoreMode != 0) {\n"
                + "        if (tacz_DepthRestoreMode == 2) {\n"
                + "            vec2 tacz_apertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));\n"
                + "            vec2 tacz_postBodyUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_PostBodyDepthSampler, 0)), vec2(1.0));\n"
                + "            float tacz_apertureDepth = texture(tacz_ApertureDepthSampler, tacz_apertureUv).r;\n"
                + "            float tacz_postBodyDepth = texture(tacz_PostBodyDepthSampler, tacz_postBodyUv).r;\n"
                + "            if (tacz_postBodyDepth != tacz_apertureDepth) {\n"
                + "                discard;\n"
                + "            }\n"
                + "        }\n"
                + "        vec2 tacz_depthSize = max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_depthUv = gl_FragCoord.xy / tacz_depthSize;\n"
                + "        gl_FragDepth = texture(depthtex2, tacz_depthUv).r;\n"
                + "        return;\n"
                + "    }\n";
        // Mode 1 keeps reticles inside the ocular. Mode 2 keeps viewmodel FX outside it; this is
        // used by both muzzle-flash layers after the cleanup draw restores ordinary world depth.
        String maskBranch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_maskWorldUv = gl_FragCoord.xy / max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskApertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));\n"
                + "        float tacz_maskWorldDepth = texture(depthtex2, tacz_maskWorldUv).r;\n"
                + "        float tacz_maskApertureDepth = texture(tacz_ApertureDepthSampler, tacz_maskApertureUv).r;\n"
                + "        bool tacz_insideOcular = tacz_maskApertureDepth < tacz_maskWorldDepth - 1.0e-6;\n"
                + "        if ((tacz_ScopeMaskMode == 1 && !tacz_insideOcular)\n"
                + "                || (tacz_ScopeMaskMode == 2 && tacz_insideOcular)) {\n"
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
            GunMod.LOGGER.info("[TACZ Scope] Injected dormant depth-restore and ocular-mask branches into Iris hand shaders.");
        }
        return patched;
    }

    private static boolean tacz$isHandProgram(String name) {
        if (name == null) {
            return false;
        }
        // ShaderCreator#createShader receives ShaderKey#getName() (for example
        // hand_cutout / hand_translucent / hand_water_bright), not only the underlying
        // shader-pack ProgramId source name (gbuffers_hand / gbuffers_hand_water).
        // Patch every first-person hand key, but keep world/entity/particle/water keys untouched.
        return name.equals("gbuffers_hand")
                || name.equals("gbuffers_hand_water")
                || name.endsWith("/gbuffers_hand")
                || name.endsWith("/gbuffers_hand_water")
                || name.equals("hand")
                || name.startsWith("hand_");
    }
}
