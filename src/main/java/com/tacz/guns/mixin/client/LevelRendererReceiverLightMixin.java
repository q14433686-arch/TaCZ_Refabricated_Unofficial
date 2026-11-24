package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.client.render.light.WorldFlashFrame;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
abstract class LevelRendererReceiverLightMixin {
    @WrapMethod(method = "submitEntities")
    private void tacz$entities(com.mojang.blaze3d.vertex.PoseStack pose, LevelRenderState state, SubmitNodeCollector out, Operation<Void> original) {
        WorldFlashFrame.submitWorld(state, () -> original.call(pose, state, out));
    }
    @WrapMethod(method = "submitBlockEntities")
    private void tacz$blocks(com.mojang.blaze3d.vertex.PoseStack pose, LevelRenderState state, net.minecraft.client.renderer.SubmitNodeStorage out, Operation<Void> original) {
        WorldFlashFrame.submitWorld(state, () -> original.call(pose, state, out));
    }
}
