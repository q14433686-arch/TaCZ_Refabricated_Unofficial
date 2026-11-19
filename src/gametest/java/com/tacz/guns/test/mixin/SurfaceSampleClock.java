package com.tacz.guns.test.mixin;

import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.test.SurfaceLightSmoke;
import com.tacz.guns.util.time.GunTime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Sample a particular instant of a 50 ms pulse for framebuffer readback on software GPUs.
 * Do not freeze gameplay, manufacture a receiver, or apply to the load/expiry measurements. */
@Mixin(value = SurfaceLights.class, remap = false)
abstract class SurfaceSampleClock {
    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/time/GunTime;nowMillis()J"))
    private static long sampleInstant() {
        return SurfaceLightSmoke.sampleTime >= 0 ? SurfaceLightSmoke.sampleTime : GunTime.nowMillis();
    }
}
