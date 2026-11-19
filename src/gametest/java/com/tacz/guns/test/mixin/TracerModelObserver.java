package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.test.TracerFrameSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockModel.class, remap = false)
abstract class TracerModelObserver {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;IIFFFF)V", at = @At("HEAD"))
    private void submit(PoseStack pose, ItemDisplayContext context, SubmitNodeCollector out, RenderType type, int light, int overlay,
                        float r, float g, float b, float a, CallbackInfo ci) {
        TracerFrameSmoke.observe(pose);
    }
}
