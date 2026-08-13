package com.tacz.guns.mixin.client.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.client.render.scope.ScopeLateReticleState;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Iris 1.10.7 run one late hand pass for a deferred TACZ scope reticle.
 *
 * <p>{@code HandRenderer#renderTranslucent} normally returns early unless vanilla finds a
 * translucent held block. A TACZ gun is not a {@code BlockItem}, so the reticle captured in
 * {@code HAND_SOLID} would otherwise have no later collector to enter. This mixin only extends
 * that gate while immutable reticle snapshots are pending. It then submits them after Iris has
 * selected {@code HAND_TRANSLUCENT}, but before Iris' normal {@code endBatch()} flush; Iris still
 * owns all FBO, shader and draw scheduling.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererReticlePassMixin {
    @Shadow private SubmitNodeStorage submitNodeCollector;

    @ModifyExpressionValue(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pathways/HandRenderer;isAnyHandTranslucent()Z"
            ),
            require = 0
    )
    private boolean tacz$runLateHandPassForScopeReticle(boolean hasVanillaTranslucentHand) {
        return hasVanillaTranslucentHand || ScopeLateReticleState.hasPendingReticles();
    }

    @Inject(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;setPhase(Lnet/irisshaders/iris/pipeline/WorldRenderingPhase;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void tacz$submitScopeReticleAfterWorldTranslucency(CallbackInfo ci) {
        // The collector is flushed later by Iris' own ItemInHandRenderer endBatch wrapper. Do not
        // draw immediately here: that would bypass the currently selected HAND_TRANSLUCENT shader.
        ScopeLateReticleState.submitPending(this.submitNodeCollector);
    }
}
