package com.tacz.guns.compat.shouldersurfing;

/**
 * Optional facade for Shoulder Surfing Reloaded.
 *
 * <p><b>26.3 status: disabled, not fixed.</b> Shoulder Surfing Reloaded has no 26.3
 * build (Modrinth's newest is {@code 26.2-5.0.7+fabric}, verified 2026-09-17), so its
 * {@code compileOnly} coordinate is commented out in {@code build.gradle} and both
 * {@link ShoulderSurfingCompatInner} and {@code ShoulderSurfingPlugin} are excluded
 * from the source set. {@link #showCrosshair()} therefore hard-returns {@code false}
 * and {@link #isInstalled()} hard-returns {@code false}, regardless of what is actually
 * loaded.</p>
 *
 * <p>To restore once Shoulder Surfing ships 26.3:</p>
 * <ol>
 *   <li>set {@code shoulder_surfing_version} in {@code gradle.properties} to the 26.3 build;</li>
 *   <li>uncomment the {@code compileOnly("maven.modrinth:shoulder-surfing-reloaded:...")} block;</li>
 *   <li>drop the two {@code com/tacz/guns/compat/shouldersurfing/...} excludes;</li>
 *   <li>restore {@code shouldersurfing_plugin.json} to {@code fabric.mod.json}'s resource set
 *       (it is currently left in place but inert, since its entrypoint class is not compiled);</li>
 *   <li>restore the {@code FabricLoader.isModLoaded} check and the delegation below.</li>
 * </ol>
 */
public final class ShoulderSurfingCompat {
    /** Kept for the restore path; unused while the integration is disabled. */
    @SuppressWarnings("unused")
    private static final String MOD_ID = "shouldersurfing";

    private ShoulderSurfingCompat() {
    }

    public static void init() {
        // 26.3: no Shoulder Surfing build exists, so there is nothing to detect.
    }

    public static boolean showCrosshair() {
        // 26.3: no-op — the third-person crosshair override is inactive.
        return false;
    }

    public static boolean isInstalled() {
        // 26.3: always false; the integration is not compiled into this build.
        return false;
    }
}
