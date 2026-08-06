package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeShaderInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the name of the Iris program that is about to be linked.
 *
 * <p>{@code ShaderCreator#link} only receives the already-transformed GLSL strings, so on its own it
 * cannot tell a hand program from terrain, entities, particles, sky or a shadow pass. Every one of
 * the four {@code ShaderCreator} paths (create / createFallback / createFallbackShadow /
 * createShadow) calls {@code ShaderPrinter.printProgram(name)} on the statement immediately before
 * its {@code link(...)} call, and the argument is exactly {@code ShaderKey#getName()}. Recording it
 * here gives the injector the program identity it needs.
 *
 * <p>Purely observational: the argument is returned unchanged. If a future Iris stops calling
 * {@code printProgram} the injector simply sees no name and skips patching entirely, which loses
 * the ocular mask under shader packs but can never emit a malformed shader.
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.ShaderPrinter", remap = false)
public abstract class IrisShaderProgramNameMixin {
    @ModifyVariable(
            method = "printProgram",
            at = @At("HEAD"),
            argsOnly = true,
            index = 0,
            require = 0
    )
    private static String tacz$captureProgramName(String name) {
        ScopeShaderInjector.setPendingProgramName(name);
        return name;
    }

    /**
     * Iris calls this once per rendering-pipeline creation, i.e. on every shader pack load, reload
     * and dimension change. Use it to drop a name left over by a pipeline that never linked and to
     * re-arm the injector's one-shot log lines, so each pack load reports on its own whether the
     * scope branches were injected.
     */
    @Inject(method = "resetPrintState", at = @At("HEAD"), require = 0)
    private static void tacz$resetScopeInjector(CallbackInfo ci) {
        ScopeShaderInjector.resetForNewPipeline();
    }
}
