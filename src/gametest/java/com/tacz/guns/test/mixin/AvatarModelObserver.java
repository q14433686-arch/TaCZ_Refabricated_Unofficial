package com.tacz.guns.test.mixin;

import com.tacz.guns.test.PalRuntimeSmoke.Frames;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class AvatarModelObserver {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at=@At("RETURN"), order=1200)
    private void countAvatar(AvatarRenderState state, CallbackInfo ci) {
        if (state.id == Frames.watched) {
            Frames.leftArm = ((PlayerModel)(Object)this).leftArm.storePose();
            Frames.count++;
        }
    }
}
