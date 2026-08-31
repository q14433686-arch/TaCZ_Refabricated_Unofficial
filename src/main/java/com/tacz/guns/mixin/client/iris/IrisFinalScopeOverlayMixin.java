package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the scope PIP lens and then the frozen reticle/rim geometry after Iris has run all
 * composite and final passes.
 *
 * <p>{@code IrisRenderingPipeline#finalizeLevelRendering()} first sets {@code isRenderingWorld}
 * false and then runs composite/final programs（Iris 26.1 分支源码逐行核对）. At TAIL Iris no
 * longer replaces core pipelines; both
 * {@link ScopePipRenderState#captureSceneAfterIrisFinal(Minecraft)} and
 * {@link ScopeFinalOverlayState} can therefore work on the main output while retaining the hand
 * projection captured during {@code HAND_SOLID}.</p>
 *
 * <p>Order is deliberately: finished shader frame -&gt; magnified PIP lens -&gt; reticle/crosshair
 * -&gt; ocular shade. When {@code ScopePipAllowShaderPacks} is off the PIP methods are no-ops and
 * this behaves exactly as before.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisFinalScopeOverlayMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), require = 1)
    private void tacz$drawScopeAfterShaderPackFinal(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ScopePipRenderState.captureSceneAfterIrisFinal(minecraft);
        ScopePipRenderState.compositeAfterIrisFinal(minecraft);
        ScopeFinalOverlayState.renderAfterFinalComposite();
    }
}
