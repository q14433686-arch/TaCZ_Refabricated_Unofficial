package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side reservation for a tube/cylinder/internal/single-shot reload or
 * a bridge clip/speedloader transfer into that internal feed.
 *
 * <p>The plan keeps two deliberately different counts:</p>
 * <ul>
 *     <li>{@code animationRounds}: the value exposed to a legacy Lua reload
 *     script as its normal magazine-fill target;</li>
 *     <li>{@code sourceRoundBudget}: real cartridges that may leave the
 *     selected source. A genuine per-round script can spend one extra round
 *     into an empty chamber before filling the magazine.</li>
 * </ul>
 *
 * <p>That separation is what lets a one-press loop remain authoritative at
 * every visual feed point rather than deferring all mutations until the final
 * reload state transition.</p>
 */
public final class InternalFeedReloadPlan {
    private final Identifier gunId;
    private final Identifier ammoId;
    private final int animationRounds;
    private final int fallbackRounds;
    private final int sourceRoundBudget;
    private final boolean tactical;
    /** Inventory slot reserved for a loading device; -1 means ordinary loose-ammo loading. */
    private final int feedDeviceSlot;
    private final ItemStack expectedFeedDevice;
    private final boolean keepEmptyFeedDevice;

    /** Source rounds promised to a Lua {@code consumeAmmoFromPlayer} call but not yet placed. */
    private int pendingScriptRounds;
    /** Source budget already promised to script calls, including pending rounds. */
    private int issuedScriptRounds;
    /** Real source rounds already moved into the gun (magazine or chamber). */
    private int transferredSourceRounds;
    /** Magazine rounds a script requested to move to the chamber on its next set-barrel call. */
    private int pendingMagazineToChamberRounds;
    /** A script touched this plan; central end-of-animation fallback must not duplicate it. */
    private boolean scriptTouched;
    /** A reserved physical source was moved, replaced, or became invalid. */
    private boolean sourceFailed;
    private boolean feedHandled;

    public InternalFeedReloadPlan(Identifier gunId, Identifier ammoId, int animationRounds, int fallbackRounds,
                                  int sourceRoundBudget, boolean tactical) {
        this(gunId, ammoId, animationRounds, fallbackRounds, sourceRoundBudget, tactical,
                -1, ItemStack.EMPTY, true);
    }

    public InternalFeedReloadPlan(Identifier gunId, Identifier ammoId, int animationRounds, int fallbackRounds,
                                  int sourceRoundBudget, boolean tactical, int feedDeviceSlot,
                                  ItemStack expectedFeedDevice, boolean keepEmptyFeedDevice) {
        this.gunId = gunId;
        this.ammoId = ammoId;
        this.animationRounds = Math.max(1, animationRounds);
        this.fallbackRounds = Math.max(0, Math.min(fallbackRounds, this.animationRounds));
        this.sourceRoundBudget = Math.max(0, sourceRoundBudget);
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

    /** Number used by legacy script timing/loop counters. */
    public int getAnimationRounds() {
        return animationRounds;
    }

    /** Number central fallback may insert when the gun uses no script feed calls. */
    public int getFallbackRounds() {
        return fallbackRounds;
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

    /**
     * Reserve up to {@code requested} source rounds for the next script-side
     * placement call. No inventory mutation happens here.
     */
    public int reserveScriptRounds(int requested) {
        scriptTouched = true;
        int granted = Math.min(Math.max(0, requested), getUnissuedSourceRounds());
        pendingScriptRounds += granted;
        issuedScriptRounds += granted;
        return granted;
    }

    /**
     * Claim source rounds that were previously returned from
     * {@code consumeAmmoFromPlayer}.  A failed/short physical extraction still
     * consumes the credit so a moved clip can never be replaced mid-animation.
     */
    public int claimReservedScriptRounds(int requested) {
        scriptTouched = true;
        int claimed = Math.min(Math.max(0, requested), pendingScriptRounds);
        pendingScriptRounds -= claimed;
        return claimed;
    }

    /**
     * Creative/non-consuming script paths call {@code putAmmoInMagazine}
     * directly. Reserve and claim their source budget atomically.
     */
    public int claimDirectScriptRounds(int requested) {
        scriptTouched = true;
        int claimed = Math.min(Math.max(0, requested), getUnissuedSourceRounds());
        issuedScriptRounds += claimed;
        return claimed;
    }

    /** Marks a script-side operation even if no source round was available. */
    public void markScriptTouched() {
        scriptTouched = true;
    }

    public boolean wasScriptTouched() {
        return scriptTouched;
    }

    public int getAvailableScriptSourceRounds() {
        return Math.max(0, sourceRoundBudget - transferredSourceRounds);
    }

    public int getUnissuedSourceRounds() {
        return Math.max(0, sourceRoundBudget - issuedScriptRounds);
    }

    public void recordSourceTransfer(int transferred) {
        transferredSourceRounds += Math.max(0, transferred);
    }

    public int getTransferredSourceRounds() {
        return transferredSourceRounds;
    }

    /** Reserve already-present internal rounds for a following set-barrel call. */
    public int reserveMagazineRoundsForChamber(int requested, int available) {
        scriptTouched = true;
        int reservable = Math.min(Math.max(0, requested),
                Math.max(0, available - pendingMagazineToChamberRounds));
        pendingMagazineToChamberRounds += reservable;
        return reservable;
    }

    public boolean claimMagazineRoundForChamber() {
        scriptTouched = true;
        if (pendingMagazineToChamberRounds <= 0) {
            return false;
        }
        pendingMagazineToChamberRounds--;
        return true;
    }

    public boolean isSourceFailed() {
        return sourceFailed;
    }

    public void markSourceFailed() {
        sourceFailed = true;
    }

    public boolean isFeedHandled() {
        return feedHandled;
    }

    public void markFeedHandled() {
        feedHandled = true;
    }
}
