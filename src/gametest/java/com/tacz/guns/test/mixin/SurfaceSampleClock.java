package com.tacz.guns.test.mixin;

import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.test.SurfaceLightSmoke;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sample a particular instant of a 50 ms pulse for framebuffer readback on software GPUs.
 * Do not freeze gameplay, manufacture a receiver, or apply to the load/expiry measurements. */
@Mixin(value = SurfaceLights.class, remap = false)
abstract class SurfaceSampleClock {
    @Inject(method = "explosion", at = @At("HEAD"))
    private static void receivedExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        SurfaceLightSmoke.observeExplosion(packet.center());
    }

    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/time/GunTime;nowMillis()J"))
    private static long sampleInstant() {
        return SurfaceLightSmoke.sampleTime >= 0 ? SurfaceLightSmoke.sampleTime : GunTime.nowMillis();
    }
}
