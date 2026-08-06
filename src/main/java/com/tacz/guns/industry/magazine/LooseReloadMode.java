package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;

/**
 * Declares what the supplied gun pack can honestly animate when a fixed
 * internal feed is loaded from loose cartridges rather than its normal loading
 * device.
 *
 * <p>This is deliberately explicit.  {@code reload.type = magazine} does not
 * tell us whether a pack has a per-round reload loop, and inventing one by
 * requiring the player to press R once per cartridge is neither faithful nor
 * a safe compatibility fallback.</p>
 */
public enum LooseReloadMode {
    /**
     * Backwards-compatible default: ordinary internal feeds retain their
     * existing complete-action reload; device-fed guns do not gain a made-up
     * loose-round animation.
     */
    @SerializedName("auto")
    AUTO,

    /** No loose-round path is exposed until a compatible animation exists. */
    @SerializedName("none")
    NONE,

    /** One native complete reload animation transfers a bounded batch. */
    @SerializedName("single_action")
    SINGLE_ACTION,

    /**
     * The gun pack's own server reload script has real repeated feed points.
     * One press of R starts that loop; the server transfers each round at the
     * corresponding script call rather than committing a whole batch at the
     * end of the animation.
     */
    @SerializedName("script_loop")
    SCRIPT_LOOP
}
