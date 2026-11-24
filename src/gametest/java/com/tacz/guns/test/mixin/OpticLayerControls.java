package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.test.ShaderOpticSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockRenderSnapshot.class)
abstract class LensCoatingControl {
    @Inject(method="submitRange",at=@At("HEAD"),cancellable=true)
    private void withoutCoating(net.minecraft.client.renderer.OrderedSubmitNodeCollector out, PoseStack pose, RenderType type, int first, int end, CallbackInfo ci) {
        if (ShaderOpticSmoke.withoutCoating && type.pipeline().getLocation().toString().equals("tacz:pipeline/scope_body_illuminated_hand")) ci.cancel();
    }
}

@Mixin(AttachmentRender.class)
abstract class MechanicalSightControl {
    @Inject(method="submitAttachment",at=@At("HEAD"),cancellable=true)
    private static void withoutIron(ItemStack part, ItemStack gun, PoseStack pose, ItemDisplayContext context,
                                    SubmitNodeCollector out, int light, int overlay, CallbackInfo ci) {
        if (ShaderOpticSmoke.withoutIron != null && context.firstPerson() && part.getItem() instanceof IAttachment a &&
                ShaderOpticSmoke.withoutIron.equals(a.getAttachmentId(part))) ci.cancel();
    }
}
