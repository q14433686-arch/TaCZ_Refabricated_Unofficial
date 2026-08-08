package com.tacz.guns.industry.magazine;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only timed inventory transaction. It deliberately keeps live menu and
 * slot references rather than serialising slot numbers: changing screens,
 * moving the carrier/source, or closing the inventory invalidates the plan
 * instead of allowing a delayed mutation against a different stack.
 */
public final class InventoryRoundHandlingPlan {
    public enum Mode {
        LOAD,
        UNLOAD
    }

    private final AbstractContainerMenu menu;
    @Nullable
    private final Slot carrierSlot;
    @Nullable
    private final Slot interactionSlot;
    @Nullable
    private final ItemStack sourceReference;
    private final int playerInventorySourceSlot;
    @Nullable
    private final ItemStack loaderReference;
    private final String carrierInstanceId;
    private final Mode mode;
    private final boolean continuous;
    private final int durationTicks;
    private int remainingTicks;
    @Nullable
    private ItemStack outputReference;

    public InventoryRoundHandlingPlan(AbstractContainerMenu menu, @Nullable Slot carrierSlot,
                                      @Nullable Slot interactionSlot, @Nullable ItemStack sourceReference,
                                      int playerInventorySourceSlot, @Nullable ItemStack loaderReference,
                                      String carrierInstanceId, Mode mode, boolean continuous, int durationTicks) {
        this.menu = menu;
        this.carrierSlot = carrierSlot;
        this.interactionSlot = interactionSlot;
        this.sourceReference = sourceReference;
        this.playerInventorySourceSlot = playerInventorySourceSlot;
        this.loaderReference = loaderReference;
        this.carrierInstanceId = carrierInstanceId == null ? "" : carrierInstanceId;
        this.mode = mode;
        this.continuous = continuous;
        this.durationTicks = Math.max(1, durationTicks);
        this.remainingTicks = this.durationTicks;
    }

    public AbstractContainerMenu getMenu() {
        return menu;
    }

    @Nullable
    public Slot getCarrierSlot() {
        return carrierSlot;
    }

    @Nullable
    public Slot getInteractionSlot() {
        return interactionSlot;
    }

    @Nullable
    public ItemStack getSourceReference() {
        return sourceReference;
    }

    public int getPlayerInventorySourceSlot() {
        return playerInventorySourceSlot;
    }

    @Nullable
    public ItemStack getLoaderReference() {
        return loaderReference;
    }

    public String getCarrierInstanceId() {
        return carrierInstanceId;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public boolean carrierIsCarried() {
        return carrierSlot == null;
    }

    public boolean isLoaderOperation() {
        return loaderReference != null;
    }

    public boolean tick() {
        return --remainingTicks <= 0;
    }

    public void resetTimer() {
        remainingTicks = durationTicks;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setOutputReference(@Nullable ItemStack outputReference) {
        this.outputReference = outputReference;
    }

    @Nullable
    public ItemStack getOutputReference() {
        return outputReference;
    }
}
