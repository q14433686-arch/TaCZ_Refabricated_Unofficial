package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.test.PlayerFeedbackSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidOffhandRender.class, remap = false)
abstract class FeedbackObservers {
    @Inject(method = "renderGunItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private static void back(LivingEntity entity, PoseStack pose, SubmitNodeCollector out, int light,
                             ItemStack stack, LayerGunShow show, int salt, CallbackInfo ci) {
        if (entity == net.minecraft.client.Minecraft.getInstance().player) PlayerFeedbackSmoke.backSubmit(salt);
    }
}
