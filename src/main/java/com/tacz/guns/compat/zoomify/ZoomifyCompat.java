package com.tacz.guns.compat.zoomify;

import com.tacz.guns.GunMod;

/**
 * Optional facade for Zoomify.
 *
 * <p><b>26.3 status: disabled, not fixed.</b> Zoomify has no 26.3 build (Modrinth's
 * newest is {@code 2.16.1+26.2}, verified 2026-09-17), so its {@code compileOnly}
 * coordinate is commented out in {@code build.gradle} and {@link ZoomifyCompatInner} —
 * the only class that touches {@code dev.isxander.zoomify} — is excluded from the
 * source set. This facade therefore hard-returns the unmodified FOV and never
 * registers the viewport hook.</p>
 *
 * <p>To restore once Zoomify ships 26.3:</p>
 * <ol>
 *   <li>set {@code zoomify_version} in {@code gradle.properties} to the 26.3 build;</li>
 *   <li>uncomment the {@code compileOnly "maven.modrinth:zoomify:..."} line;</li>
 *   <li>drop the {@code com/tacz/guns/compat/zoomify/ZoomifyCompatInner.java} exclude;</li>
 *   <li>restore the body of {@link #init()} and {@link #getFov(double, float)} to delegate
 *       to {@link ZoomifyCompatInner} (see that class' javadoc for the original shape).</li>
 * </ol>
 */
public class ZoomifyCompat {
    /** Kept for the restore path; unused while the integration is disabled. */
    @SuppressWarnings("unused")
    private static final String MOD_ID = "zoomify";

    public static void init() {
        // 26.3: no Zoomify build exists, so there is nothing to hook into.
        GunMod.LOGGER.info("[TACZ Zoomify] No Zoomify integration in this build "
                + "(Zoomify has no Minecraft 26.3 release); scope FOV is unaffected.");
    }

    public static double getFov(double fov, float tickDelta) {
        // 26.3: no-op — returns the FOV unchanged.
        return fov;
    }
}
