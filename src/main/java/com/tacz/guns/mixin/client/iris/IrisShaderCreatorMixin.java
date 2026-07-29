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

        int declarationPos = 0;
        if (source.startsWith("#version")) {
            int lineEnd = source.indexOf('\n');
            if (lineEnd >= 0) {
                declarationPos = lineEnd + 1;
            }
        }

        String declarations = "\n// TACZ Iris scope mask bridge: 0=off, 1=body discard-inside, 2=reticle discard-outside\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ScopeMaskSampler;\n";
        String branch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_scopeMaskUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ScopeMaskSampler, 0)), vec2(1.0));\n"
                + "        bool tacz_insideScope = texture(tacz_ScopeMaskSampler, tacz_scopeMaskUv).r > 0.5;\n"
                + "        if ((tacz_ScopeMaskMode == 1 && tacz_insideScope) || (tacz_ScopeMaskMode == 2 && !tacz_insideScope)) {\n"
                + "            discard;\n"
                + "        }\n"
                + "    }\n";

        String withDeclarations = source.substring(0, declarationPos) + declarations + source.substring(declarationPos);
        int adjustedBrace = brace + declarations.length();
        return withDeclarations.substring(0, adjustedBrace + 1) + branch + withDeclarations.substring(adjustedBrace + 1);
    }
}
