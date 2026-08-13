package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders frozen scope reticle/rim geometry only after Iris has run all composite and final passes.
 *
 * <p>{@code IrisRenderingPipeline#finalizeLevelRendering()} first sets {@code isRenderingWorld}
 * false and then runs composite/final programs. At TAIL Iris no longer replaces core pipelines;
 * {@link ScopeFinalOverlayState} can therefore use its no-fog vanilla fragments on the main output
 * while retaining the hand projection captured during {@code HAND_SOLID}.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisFinalScopeOverlayMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), require = 1)
    private void tacz$drawScopeAfterShaderPackFinal(CallbackInfo ci) {
        ScopeFinalOverlayState.renderAfterFinalComposite();
    }
}
