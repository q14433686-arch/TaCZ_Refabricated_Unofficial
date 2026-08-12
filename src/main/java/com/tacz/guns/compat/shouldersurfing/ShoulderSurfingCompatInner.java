package com.tacz.guns.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.client.InputHandler;

/**
 * Calls into Shoulder Surfing Reloaded's 26.2 / API 5.x client classes.
 *
 * <p>This class is only reached through {@link ShoulderSurfingCompat} after the
 * {@code shouldersurfing} mod-id check, so Shoulder Surfing remains an optional
 * runtime dependency.</p>
 */
public final class ShoulderSurfingCompatInner {
    private ShoulderSurfingCompatInner() {
    }

    public static boolean showCrosshair() {
        return Perspective.current() == Perspective.SHOULDER_SURFING
                && !InputHandler.FREE_LOOK.isDown();
    }
}
