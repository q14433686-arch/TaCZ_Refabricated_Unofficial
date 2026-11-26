package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.Storage.class)
abstract class ThermalBodyNodesMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void tacz$copyBody(RenderType type, ModelSubmit<?> node, CallbackInfo ci) {
        ScopeThermalCapture.record(type, node);
    }
}
