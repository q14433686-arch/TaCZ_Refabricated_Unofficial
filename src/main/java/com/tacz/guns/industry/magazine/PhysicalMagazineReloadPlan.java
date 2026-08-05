package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server-only reservation for one physical magazine reload.
 *
 * <p>The magazine remains in the player's inventory until the existing gun
 * animation reaches its feed point.  The plan records the exact slot and
 * component-bearing stack selected at reload start; moving or replacing it
 * during the animation makes the swap fail closed rather than silently falling
 * back to loose ammunition.</p>
 */
public final class PhysicalMagazineReloadPlan {
    private final Identifier gunId;
    private final boolean tactical;
    private final boolean consumeMagazine;
    private final int sourceSlot;
    private final ItemStack expectedMagazine;
    private boolean feedHandled;

    public PhysicalMagazineReloadPlan(Identifier gunId, boolean tactical, boolean consumeMagazine,
                                      int sourceSlot, ItemStack expectedMagazine) {
        this.gunId = gunId;
        this.tactical = tactical;
        this.consumeMagazine = consumeMagazine;
        this.sourceSlot = sourceSlot;
        this.expectedMagazine = expectedMagazine.copy();
    }

    public Identifier getGunId() {
        return gunId;
    }

    public boolean isTactical() {
        return tactical;
    }

    public boolean consumesMagazine() {
        return consumeMagazine;
    }

    public int getSourceSlot() {
        return sourceSlot;
    }

    public ItemStack getExpectedMagazine() {
        return expectedMagazine.copy();
    }

    public boolean isFeedHandled() {
        return feedHandled;
    }

    public void markFeedHandled() {
        this.feedHandled = true;
    }
}
