package com.tacz.guns.test.mixin;

import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.test.SurfaceLightSmoke;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the same packet/collection for ON and OFF, without altering time or gameplay. */
@Mixin(value = SurfaceLights.class, remap = false)
abstract class SurfaceLightObserver {
    @Inject(method = "explosion", at = @At("RETURN"))
    private static void receivedExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        SurfaceLightSmoke.observeExplosion(packet.center());
    }

    // Gametest only: CPU time spent in admission cleanup, raycasts, patch assembly and
    // queueing. This does NOT measure deferred vertex writes, GPU fill or the entire frame.
    @Inject(method = "submit", at = @At("HEAD"))
    private static void beginCollect(CallbackInfo ci) { SurfaceLightSmoke.beginCollect(); }
    @Inject(method = "submit", at = @At("RETURN"))
    private static void endCollect(CallbackInfo ci) { SurfaceLightSmoke.endCollect(); }
}
