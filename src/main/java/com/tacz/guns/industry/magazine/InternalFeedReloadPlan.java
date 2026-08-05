package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;

/** Server-side reservation for a tube/cylinder/internal-box/single-shot reload. */
public final class InternalFeedReloadPlan {
    private final Identifier gunId;
    private final Identifier ammoId;
    private final int rounds;
    private final boolean tactical;
    private boolean feedHandled;

    public InternalFeedReloadPlan(Identifier gunId, Identifier ammoId, int rounds, boolean tactical) {
        this.gunId = gunId;
        this.ammoId = ammoId;
        this.rounds = Math.max(1, rounds);
        this.tactical = tactical;
    }

    public Identifier getGunId() {
        return gunId;
    }

    public Identifier getAmmoId() {
        return ammoId;
    }

    public int getRounds() {
        return rounds;
    }

    public boolean isTactical() {
        return tactical;
    }

    public boolean isFeedHandled() {
        return feedHandled;
    }

    public void markFeedHandled() {
        feedHandled = true;
    }
}
