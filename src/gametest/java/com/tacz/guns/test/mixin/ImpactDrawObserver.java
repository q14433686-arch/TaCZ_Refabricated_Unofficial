package com.tacz.guns.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.functional.ImpactMarkerRender;
import com.tacz.guns.test.ImpactMarkerSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The control omits only the marker node. Never queue an empty mesh or compute a second hit. */
@Mixin(value = ImpactMarkerRender.class, remap = false)
abstract class ImpactDrawObserver {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void captureInputs(ItemStack stack, PoseStack root, SubmitNodeCollector collector, ImpactMarkerRender.Layer layer, int overlay, CallbackInfo ci) {
        if (ImpactMarkerSmoke.onSubmit((ImpactMarkerRender)(Object)this, root, layer)) ci.cancel();
    }

    @ModifyExpressionValue(method = "submit", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/papi/RangefinderReadout;impactSlope(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/client/resource/pojo/display/gun/Rangefinder;)D"))
    private double inspectSample(double original) {
        ImpactMarkerSmoke.onSample(original);
        return original;
    }

    @Inject(method = "drawBox", at = @At("HEAD"))
    private static void beforeDraw(CallbackInfo ci) { ImpactMarkerSmoke.atDraw(); }
}
