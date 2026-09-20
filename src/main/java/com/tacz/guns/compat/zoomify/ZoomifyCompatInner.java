package com.tacz.guns.compat.zoomify;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.tacz.guns.GunMod;
import dev.isxander.zoomify.Zoomify;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The Zoomify-facing half of the compat, split out of {@link ZoomifyCompat} during the
 * 26.3 port.
 *
 * <p><b>This class is excluded from compilation</b> in {@code build.gradle}: Zoomify has
 * no 26.3 build (Modrinth's newest is {@code 2.16.1+26.2}, verified 2026-09-17), so
 * {@code dev.isxander.zoomify.Zoomify} does not resolve. It is kept in the tree verbatim
 * — this is exactly the 26.2 implementation — so that restoring the integration is a
 * matter of re-adding the dependency and deleting the exclude, not rewriting logic.</p>
 *
 * <p>Restore procedure is documented on {@link ZoomifyCompat}.</p>
 */
public final class ZoomifyCompatInner {
    private static final String MOD_ID = "zoomify";
    private static boolean INSTALLED = false;

    private ZoomifyCompatInner() {
    }

    public static void init() {
        INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (INSTALLED) {
            ViewportEvent.FOV.register(event -> event.setFOV(getFov(event.getFOV(), (float) event.getPartialTick())));
        }
    }

    public static double getFov(double fov, float tickDelta) {
        if (INSTALLED) {
            try {
                return fov / Zoomify.getZoomDivisor(tickDelta);
            } catch (Exception e) {
                GunMod.LOGGER.error("Error while getting Zoomify zoom divisor: " + e.getMessage());
                return fov;
            }
        }
        return fov;
    }
}
