package com.tacz.guns.compat.zoomify;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.tacz.guns.GunMod;
import dev.isxander.zoomify.Zoomify;

/** Loaded only after Fabric Loader confirms the optional mod is present. */
final class ZoomifyCompatInner {
    static void init() {
        ViewportEvent.FOV.register(event -> event.setFOV(getFov(event.getFOV(), (float) event.getPartialTick())));
    }

    static double getFov(double fov, float tickDelta) {
        try {
            return fov / Zoomify.getZoomDivisor(tickDelta);
        } catch (RuntimeException exception) {
            GunMod.LOGGER.error("Failed to get Zoomify FOV", exception);
            return fov;
        }
    }

    private ZoomifyCompatInner() {}
}
