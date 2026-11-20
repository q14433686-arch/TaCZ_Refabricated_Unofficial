package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.light.SurfaceLights;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RETURN is after vanilla's same-thread check. Observe one packet, never its child particles. */
@Mixin(ClientPacketListener.class)
abstract class DetonationLightMixin {
    @Inject(method="handleExplosion", at=@At("RETURN"))
    private void tacz$blastLight(ClientboundExplodePacket packet, CallbackInfo ci) { SurfaceLights.explosion(packet); }

    @Inject(method="handleParticleEvent", at=@At("RETURN"))
    private void tacz$stunLight(ClientboundLevelParticlesPacket packet, CallbackInfo ci) { SurfaceLights.stun(packet); }
}
