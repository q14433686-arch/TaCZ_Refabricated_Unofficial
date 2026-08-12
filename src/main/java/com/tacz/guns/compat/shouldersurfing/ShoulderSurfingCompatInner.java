package com.tacz.guns.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.client.InputHandler;

/** Direct calls isolated behind {@link ShoulderSurfingCompat}'s installed-mod guard. */
public final class ShoulderSurfingCompatInner {
    private ShoulderSurfingCompatInner() {
    }

    public static boolean showCrosshair() {
        return Perspective.current() == Perspective.SHOULDER_SURFING && !InputHandler.FREE_LOOK.isDown();
    }
}
