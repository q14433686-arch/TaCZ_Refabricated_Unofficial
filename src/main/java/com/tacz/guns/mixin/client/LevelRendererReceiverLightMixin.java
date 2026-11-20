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
    @WrapMethod(method = "submitFeatures")
    private void tacz$worldFlash(LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, Operation<Void> original) {
        WorldFlashFrame.submitWorld(state, () -> original.call(state, collector, renderOutline));
    }
}
