package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 FeatureRenderDispatcher phase-boundary hooks:
 * 1) renderAllFeatures HEAD: render ocular mask, composite PIP lens, and snapshot hand matrices
 *    before solid features draw the scope body and reticle.
 * 2) renderSolidFeatures RETURN: consume poly_mesh world GPU draws inside the world pass.
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    @Inject(method = "renderAllFeatures", at = @At("HEAD"))
    private void tacz$scopeMaskAtPhaseBoundary(CallbackInfo ci) {
        if (ScopeFinalOverlayState.isReplaying() || ScopeThermalCapture.isReplaying()) {
            return;
        }
        ScopeMaskRenderer.renderAtPhaseBoundary();
        ScopePipRenderer.compositeAtPhaseBoundary();
        ScopeFinalOverlayState.capturePhaseBoundaryTransform();
    }

    @Inject(method = "renderSolidFeatures", at = @At(value = "RETURN"), require = 0)
    private void tacz$consumeMeshGpuWorldFlush(CallbackInfo ci) {
        if (ScopeFinalOverlayState.isReplaying() || ScopeThermalCapture.isReplaying()) {
            return;
        }
        PolyMeshGpuRenderer.renderAtWorldFlush();
    }
}
