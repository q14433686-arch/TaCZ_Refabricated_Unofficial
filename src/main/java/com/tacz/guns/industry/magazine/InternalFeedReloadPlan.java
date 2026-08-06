package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side reservation for a tube/cylinder/internal/single-shot reload or
 * a bridge clip/speedloader transfer into that internal feed.
 */
public final class InternalFeedReloadPlan {
    private final Identifier gunId;
    private final Identifier ammoId;
    private final int rounds;
    private final boolean tactical;
    /** Inventory slot reserved for a loading device; -1 means ordinary loose-ammo loading. */
    private final int feedDeviceSlot;
    private final ItemStack expectedFeedDevice;
    private final boolean keepEmptyFeedDevice;
    private boolean feedHandled;

    public InternalFeedReloadPlan(Identifier gunId, Identifier ammoId, int rounds, boolean tactical) {
        this(gunId, ammoId, rounds, tactical, -1, ItemStack.EMPTY, true);
    }

    public InternalFeedReloadPlan(Identifier gunId, Identifier ammoId, int rounds, boolean tactical,
                                  int feedDeviceSlot, ItemStack expectedFeedDevice, boolean keepEmptyFeedDevice) {
        this.gunId = gunId;
        this.ammoId = ammoId;
        this.rounds = Math.max(1, rounds);
        this.tactical = tactical;
        this.feedDeviceSlot = feedDeviceSlot;
        this.expectedFeedDevice = expectedFeedDevice == null ? ItemStack.EMPTY : expectedFeedDevice.copy();
        this.keepEmptyFeedDevice = keepEmptyFeedDevice;
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

    public boolean usesFeedDevice() {
        return feedDeviceSlot >= 0 && !expectedFeedDevice.isEmpty();
    }

    public int getFeedDeviceSlot() {
        return feedDeviceSlot;
    }

    public ItemStack getExpectedFeedDevice() {
        return expectedFeedDevice.copy();
    }

    public boolean keepEmptyFeedDevice() {
        return keepEmptyFeedDevice;
    }

    public boolean isFeedHandled() {
        return feedHandled;
    }

    public void markFeedHandled() {
        feedHandled = true;
    }
}
