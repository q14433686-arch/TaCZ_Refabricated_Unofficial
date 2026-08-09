package com.tacz.guns.mixin.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the ocular mask rendering at the phase boundary in FeatureRenderDispatcher.
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    private static boolean tacz$loggedInjection;

    /**
     * Primary injection: before executeSolid.
     * If PreparedFrame/executeSolid doesn't exist in 26.1.2's bytecode, this silently skips.
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
        if (!tacz$loggedInjection) {
            tacz$loggedInjection = true;
            GunMod.LOGGER.info("[TACZ Scope] executeSolid injection ACTIVE");
        }
        ScopeMaskRenderer.renderAtPhaseBoundary();
    }

    /**
     * Fallback: HEAD of renderAllFeatures.
     * Always fires if the class exists, regardless of inner class structure.
     */
    @Inject(method = "renderAllFeatures", at = @At("HEAD"), require = 0)
    private void tacz$scopeMaskFallback(CallbackInfo ci) {
        if (!tacz$loggedInjection) {
            tacz$loggedInjection = true;
            GunMod.LOGGER.info("[TACZ Scope] HEAD fallback injection ACTIVE, inHandPass={}",
                    ScopeMaskRenderer.isInHandPass());
        }
        ScopeMaskRenderer.renderAtPhaseBoundary();
    }
}
