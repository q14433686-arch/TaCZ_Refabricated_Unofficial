package com.tacz.guns.test.mixin;

import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.test.SensorLayers;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScopeFinalOverlayState.class)
abstract class SensorLayerObserver {
    @Inject(method="renderAfterLevelComposite",at=@At("HEAD"))
    private static void beforeDisplayInk(CallbackInfo ci) {
        if (SensorLayers.requested()) SensorLayers.observe(Minecraft.getInstance().gameRenderer.mainRenderTarget(),CurrentHeatTarget.get());
    }
}
