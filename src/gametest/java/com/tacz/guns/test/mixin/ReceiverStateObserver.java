package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.test.ReceiverInputs;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class ReceiverStateObserver {
    @Inject(method="submitEntities", at=@At("HEAD"))
    private void inputs(PoseStack pose, LevelRenderState state, SubmitNodeCollector output, CallbackInfo ci) {
        ReceiverInputs.submit(state, true);
    }
    @Inject(method="submitBlockEntities", at=@At("HEAD"))
    private void blocks(PoseStack pose, LevelRenderState state, net.minecraft.client.renderer.SubmitNodeStorage output, CallbackInfo ci) {
        ReceiverInputs.submit(state, false);
    }
}
