package com.tacz.guns.compat.zoomify;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.tacz.guns.GunMod;
import dev.isxander.zoomify.Zoomify;

final class ZoomifyCompatInner {
    static void init() {
        ViewportEvent.FOV.register(event -> event.setFOV(getFov(event.getFOV(), (float)event.getPartialTick())));
    }
    static double getFov(double fov, float tickDelta) {
        try { return fov / Zoomify.getZoomDivisor(tickDelta); }
        catch (RuntimeException e) { GunMod.LOGGER.error("Failed to get Zoomify FOV", e); return fov; }
    }
    private ZoomifyCompatInner() {}
}
