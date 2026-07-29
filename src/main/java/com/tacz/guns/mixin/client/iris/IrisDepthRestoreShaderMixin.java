package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds a dormant depth-restore branch to Iris linked fragment shaders. */
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
    private static String tacz$injectDepthRestore(String source) {
        if (source == null || source.contains("tacz_DepthRestoreMode")) {
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
        String declarations = "\n// TACZ ocular depth restore; dormant for every ordinary draw\n"
                + "uniform int tacz_DepthRestoreMode;\n"
                + "uniform sampler2D tacz_DepthBackupSampler;\n";
        String branch = "\n    if (tacz_DepthRestoreMode != 0) {\n"
                + "        vec2 tacz_depthSize = max(vec2(textureSize(tacz_DepthBackupSampler, 0)), vec2(1.0));\n"
                + "        vec2 tacz_depthUv = gl_FragCoord.xy / tacz_depthSize;\n"
                + "        gl_FragDepth = texture(tacz_DepthBackupSampler, tacz_depthUv).r;\n"
                + "        return;\n"
                + "    }\n";

        String withDeclarations = source.substring(0, declarationPos)
                + declarations + source.substring(declarationPos);
        int adjustedBrace = brace + declarations.length();
        String patched = withDeclarations.substring(0, adjustedBrace + 1)
                + branch + withDeclarations.substring(adjustedBrace + 1);
        if (!tacz$loggedPatch) {
            tacz$loggedPatch = true;
            GunMod.LOGGER.info("[TACZ Scope] Injected dormant depth-restore branch into Iris shaders.");
        }
        return patched;
    }
}
