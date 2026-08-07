package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side reservation for inserting one physical en-bloc clip. Unlike a
 * bridge clip, the selected stack is installed in the gun and remains there
 * until its final round is fired and the empty clip is automatically ejected.
 */
public final class EnBlocClipReloadPlan {
    private final Identifier gunId;
    private final boolean tactical;
    private final boolean consumesClip;
    private final int clipSlot;
    private final ItemStack expectedClip;
    private boolean feedHandled;

    public EnBlocClipReloadPlan(Identifier gunId, boolean tactical, boolean consumesClip,
                                int clipSlot, ItemStack expectedClip) {
        this.gunId = gunId;
        this.tactical = tactical;
        this.consumesClip = consumesClip;
        this.clipSlot = clipSlot;
        this.expectedClip = expectedClip == null ? ItemStack.EMPTY : expectedClip.copy();
    }

    public Identifier getGunId() {
        return gunId;
    }

    public boolean isTactical() {
        return tactical;
    }

    public boolean consumesClip() {
        return consumesClip;
    }

    public int getClipSlot() {
        return clipSlot;
    }

    public ItemStack getExpectedClip() {
        return expectedClip.copy();
    }

    public boolean isFeedHandled() {
        return feedHandled;
    }

    public void markFeedHandled() {
        feedHandled = true;
    }
}
