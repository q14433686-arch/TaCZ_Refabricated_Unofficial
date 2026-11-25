package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.test.ConstraintPoseSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
abstract class ConstraintPoseObserver {
    @Inject(method = "applyAnimationConstraintTransform", at = @At("HEAD"))
    private static void beforeConstraint(PoseStack stack, BedrockGunModel model, float weight, CallbackInfo ci) {
        ConstraintPoseSmoke.begin(stack, model, weight);
    }
    @Inject(method = "applyAnimationConstraintTransform", at = @At("RETURN"))
    private static void afterConstraint(PoseStack stack, BedrockGunModel model, float weight, CallbackInfo ci) {
        ConstraintPoseSmoke.end(stack);
    }
}
