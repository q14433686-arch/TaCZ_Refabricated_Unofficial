package com.tacz.guns.test.mixin;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import com.tacz.guns.test.PlayerFeedbackSmoke;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ServerMessageBulletHitBlock.class, remap=false)
abstract class ImpactPacketObserver {
    @Inject(method="handle(Lcom/tacz/guns/network/message/ServerMessageBulletHitBlock;Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$Context;Z)V", at=@At("HEAD"))
    private static void received(ServerMessageBulletHitBlock message,ClientPlayNetworking.Context ctx,boolean incendiary,CallbackInfo ci) {
        PlayerFeedbackSmoke.impactPackets.incrementAndGet();
        if (incendiary) PlayerFeedbackSmoke.incendiaryPackets.incrementAndGet();
    }
}
