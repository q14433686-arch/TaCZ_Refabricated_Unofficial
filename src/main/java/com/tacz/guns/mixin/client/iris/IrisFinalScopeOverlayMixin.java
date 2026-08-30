package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders frozen scope reticle/rim geometry only after Iris has run all composite and final passes.
 *
 * <p>{@code IrisRenderingPipeline#finalizeLevelRendering()}（Iris 26.1 分支源码逐行核对）先置
 * {@code isRenderingWorld = false}，再执行 {@code compositeRenderer.renderAll()} 与
 * {@code finalPassRenderer.renderFinalPass()}。在 TAIL 处 Iris 不再接管核心管线，因此
 * {@link ScopeFinalOverlayState} 可以用无雾的 vanilla 片元直接画到主输出上，同时保留在
 * {@code HAND_SOLID} 期间捕获的手部投影。</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisFinalScopeOverlayMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), require = 1)
    private void tacz$drawScopeAfterShaderPackFinal(CallbackInfo ci) {
        ScopeFinalOverlayState.renderAfterFinalComposite();
    }
}
