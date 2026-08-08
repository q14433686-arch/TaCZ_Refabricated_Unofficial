package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the ocular mask rendering at the phase boundary in FeatureRenderDispatcher.
 *
 * <h2>Why this location</h2>
 * The rendering structure is:
 * <pre>
 * renderAllFeatures() {
 *     PreparedFrame f = prepareFrame(storage);   // prepare only, no draws
 *     f.executeSolid();                          // actual draws happen here
 *     f.executeTranslucent();
 *     f.executeTranslucentAfterTerrain();
 *     f.executeAlwaysOnTop();
 *     f.close();                                 // cleanup
 * }
 * </pre>
 * Between {@code prepareFrame} and {@code executeSolid}, no render pass is active.
 * This is the ONLY safe point to open a new render pass targeting a different FBO
 * (the mask FBO) without causing state conflicts.</p>
 *
 * <h2>Timing</h2>
 * The mask must be ready BEFORE the solid pass, because the scope body draws in
 * the solid pass and needs to sample the mask texture.</p>
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    /**
     * Render the ocular mask before the solid phase.
     *
     * <p>Injection target: INVOKE + executeSolid + BEFORE.
     * If the inner class name differs in 26.1.2 (e.g. no $PreparedFrame),
     * the require=0 default in the mixin config will let this silently skip,
     * and the mask will simply not render (graceful degradation).</p>
     */
    @Inject(
            method = "renderAllFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void tacz$scopeMaskAtPhaseBoundary(CallbackInfo ci) {
        ScopeMaskRenderer.renderAtPhaseBoundary();
    }
}
