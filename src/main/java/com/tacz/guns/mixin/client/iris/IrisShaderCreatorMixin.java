package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Injects a dormant TACZ scope-mask branch into Iris fragment shaders before they are linked. */
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
        if (source == null) {
            return null;
        }
        String patched = tacz$injectScopeMask(source);
        if (patched != source && !tacz$loggedPatch) {
            tacz$loggedPatch = true;
            GunMod.LOGGER.info("[TACZ Scope] Injecting dormant scope-mask branch into Iris linked fragment shaders.");
        }
        return patched;
    }

    private static String tacz$injectScopeMask(String source) {
        if (source.contains("tacz_ScopeMaskMode")) {
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

        String declarations = "\n// TACZ Iris scope mask bridge: 0=off, 1=body discard-inside-mask, 2=reticle discard-outside-window, 3=window discard-inside-window\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ScopeMaskSampler;\n\n";

        // 与 scope_body.fsh 的 SCOPE_MASK 段保持同步（2026-08-10 第 26 轮）：
        //  - 窗口 = 掩码向内收缩 progress×(1-0.65)×带宽，全开时保留黑圈（不再有
        //    progress<0.999 的跳过）；
        //  - mode 1 = 镜身：掩码(A)内 discard；mode 2 = 准星：只保留窗口内；
        //    mode 3 = 目镜黑圈/枪体/配件：窗口内 discard。
        String branch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_scopeMaskUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ScopeMaskSampler, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskSample = texture(tacz_ScopeMaskSampler, tacz_scopeMaskUv).rg;\n"
                + "        bool tacz_inMask = tacz_maskSample.r > 0.5;\n"
                + "        bool tacz_inWindow = false;\n"
                + "        if (tacz_inMask) {\n"
                + "            float tacz_progress = clamp(tacz_maskSample.g, 0.0, 1.0);\n"
                + "            const int RINGS = 3;\n"
                + "            const int STEPS = 8;\n"
                + "            float inside = 0.0;\n"
                + "            float total = 0.0;\n"
                + "            float unit = 0.055;\n"
                + "            vec2 tacz_texSize = vec2(textureSize(tacz_ScopeMaskSampler, 0));\n"
                + "            for (int r = 1; r <= RINGS; r++) {\n"
                + "                float radius = unit * float(r) / float(RINGS);\n"
                + "                for (int i = 0; i < STEPS; i++) {\n"
                + "                    float a = 6.2831853 * float(i) / float(STEPS);\n"
                + "                    vec2 off = vec2(cos(a), sin(a)) * radius;\n"
                + "                    off.x *= tacz_texSize.y / max(tacz_texSize.x, 1.0);\n"
                + "                    total += 1.0;\n"
                + "                    inside += texture(tacz_ScopeMaskSampler, tacz_scopeMaskUv + off).r > 0.5 ? 1.0 : 0.0;\n"
                + "                }\n"
                + "            }\n"
                + "            float depth = total > 0.0 ? inside / total : 1.0;\n"
                + "            float threshold = 1.0 - 0.5 * tacz_progress * (1.0 - 0.65);\n"
                + "            tacz_inWindow = depth >= threshold;\n"
                + "        }\n"
                + "        if ((tacz_ScopeMaskMode == 1 && tacz_inMask) || (tacz_ScopeMaskMode == 2 && !tacz_inWindow) || (tacz_ScopeMaskMode == 3 && tacz_inWindow)) {\n"
                + "            discard;\n"
                + "        }\n"
                + "    }\n";

        String beforeMain = source.substring(0, main);
        String afterMain = source.substring(main);
        int newBrace = afterMain.indexOf('{');
        return beforeMain + declarations + afterMain.substring(0, newBrace + 1) + branch + afterMain.substring(newBrace + 1);
    }
}
