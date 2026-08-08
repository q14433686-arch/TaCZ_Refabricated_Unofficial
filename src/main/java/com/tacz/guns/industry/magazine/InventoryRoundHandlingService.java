package com.tacz.guns.industry.magazine;

import cn.sh1rocu.tacz.api.event.PlayerTickEvent;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.industry.ammo.AmmoProfileService;
import com.tacz.guns.init.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Timed per-round handling inside the player's existing inventory GUI.
 *
 * <p>No new workstation is involved: a magazine held on the cursor and a
 * clicked inventory slot are the real input/output slots. The server records a
 * short-lived menu/slot transaction, validates it every tick, and mutates one
 * physical round only when its timer reaches zero.</p>
 */
public final class InventoryRoundHandlingService {
    private InventoryRoundHandlingService() {
    }

    /** Called by MagazineItem's normal inventory secondary-click hook. */
    public static boolean beginMagazineInteraction(Player player, ItemStack carrier, Slot target) {
        if (player.level().isClientSide() || !isFeatureEnabled()
                || !(carrier.getItem() instanceof MagazineItemDataAccessor magazine)
                || !magazine.isConfigured(carrier)) {
            return false;
        }
        if (hasActivePlan(player)) {
            return true;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return false;
        }
        String carrierId = ensureCarrierIdentity(magazine, carrier);
        // The hook receives the carried stack in normal menus; write it back
        // explicitly so its stable transaction id is visible to server/client
        // menu state before the first timed tick.
        menu.setCarried(carrier);
        ItemStack targetStack = target.getItem();
        if (targetStack.getItem() instanceof IAmmo ammo && canLoad(magazine, carrier, targetStack, ammo)) {
            begin(player, new InventoryRoundHandlingPlan(menu, null, target, targetStack, -1,
                    null, carrierId, InventoryRoundHandlingPlan.Mode.LOAD,
                    player.isShiftKeyDown(), durationTicks(false, true)));
            return true;
        }
        if (targetStack.isEmpty() && canUnload(magazine, carrier, target, null)) {
            begin(player, new InventoryRoundHandlingPlan(menu, null, target, null, -1,
                    null, carrierId, InventoryRoundHandlingPlan.Mode.UNLOAD,
                    player.isShiftKeyDown(), durationTicks(false, false)));
            return true;
        }
        return false;
    }

    /**
     * Keeps the existing loader-on-magazine inventory interaction, but converts
     * its old instant bulk extraction into a timed sequence from one real
     * compatible source stack. The player can pick a profile by arranging the
     * chosen loose-ammo stack earlier in their inventory order.
     */
    public static boolean beginLoaderInteraction(Player player, ItemStack loader, Slot carrierSlot) {
        if (player.level().isClientSide() || !isFeatureEnabled()
                || !loader.is(ModItems.MAGAZINE_LOADER)
                || !(carrierSlot.getItem().getItem() instanceof MagazineItemDataAccessor magazine)
                || !magazine.isConfigured(carrierSlot.getItem())) {
            return false;
        }
        if (hasActivePlan(player)) {
            return true;
        }
        int sourceSlot = findCompatibleInventorySource(player, magazine, carrierSlot.getItem());
        if (sourceSlot < 0) {
            return false;
        }
        ItemStack source = player.getInventory().getItem(sourceSlot);
        String carrierId = ensureCarrierIdentity(magazine, carrierSlot.getItem());
        carrierSlot.setChanged();
        begin(player, new InventoryRoundHandlingPlan(player.containerMenu, carrierSlot, null, source, sourceSlot,
                loader, carrierId, InventoryRoundHandlingPlan.Mode.LOAD, true, durationTicks(true, true)));
        return true;
    }

    /** Server-side player tick; client ticks intentionally do nothing. */
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ShooterDataHolder data = IGunOperator.fromLivingEntity(player).getDataHolder();
        InventoryRoundHandlingPlan plan = data.inventoryRoundHandling;
        if (plan == null) {
            return;
        }
        if (!validatePlan(player, plan)) {
            cancel(data);
            player.containerMenu.broadcastFullState();
            return;
        }
        if (!plan.tick()) {
            return;
        }
        boolean completed = plan.getMode() == InventoryRoundHandlingPlan.Mode.LOAD
                ? completeLoad(player, plan) : completeUnload(player, plan);
        if (!completed) {
            cancel(data);
            player.containerMenu.broadcastFullState();
            return;
        }
        player.containerMenu.broadcastFullState();
        if (plan.isContinuous() && validatePlan(player, plan) && canContinue(player, plan)) {
            plan.resetTimer();
        } else {
            cancel(data);
            player.containerMenu.broadcastFullState();
        }
    }

    public static void cancel(Player player) {
        cancel(IGunOperator.fromLivingEntity(player).getDataHolder());
    }

    private static void begin(Player player, InventoryRoundHandlingPlan plan) {
        IGunOperator.fromLivingEntity(player).getDataHolder().inventoryRoundHandling = plan;
        player.containerMenu.broadcastFullState();
    }

    private static boolean hasActivePlan(Player player) {
        return IGunOperator.fromLivingEntity(player).getDataHolder().inventoryRoundHandling != null;
    }

    private static void cancel(ShooterDataHolder data) {
        data.inventoryRoundHandling = null;
    }

    private static boolean validatePlan(Player player, InventoryRoundHandlingPlan plan) {
        if (player.containerMenu != plan.getMenu()) {
            return false;
        }
        if (plan.isLoaderOperation()) {
            ItemStack carriedLoader = player.containerMenu.getCarried();
            if (carriedLoader.isEmpty() || !ItemStack.isSameItemSameComponents(carriedLoader, plan.getLoaderReference())) {
                return false;
            }
        }
        ItemStack carrier = carrierStack(player, plan);
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine)
                || !magazine.isConfigured(carrier)
                || !plan.getCarrierInstanceId().equals(magazine.getRoundHandlingInstanceId(carrier))) {
            return false;
        }
        if (plan.getMode() == InventoryRoundHandlingPlan.Mode.LOAD) {
            ItemStack source = sourceStack(player, plan);
            return source != null && source == plan.getSourceReference()
                    && source.getItem() instanceof IAmmo ammo
                    && canLoad(magazine, carrier, source, ammo);
        }
        Slot output = plan.getInteractionSlot();
        return output != null && canUnload(magazine, carrier, output, plan.getOutputReference());
    }

    private static boolean canContinue(Player player, InventoryRoundHandlingPlan plan) {
        ItemStack carrier = carrierStack(player, plan);
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine)) {
            return false;
        }
        if (plan.getMode() == InventoryRoundHandlingPlan.Mode.LOAD) {
            ItemStack source = sourceStack(player, plan);
            return source != null && source.getItem() instanceof IAmmo ammo && canLoad(magazine, carrier, source, ammo);
        }
        Slot output = plan.getInteractionSlot();
        return output != null && canUnload(magazine, carrier, output, plan.getOutputReference());
    }

    private static boolean completeLoad(Player player, InventoryRoundHandlingPlan plan) {
        ItemStack carrier = carrierStack(player, plan);
        ItemStack source = sourceStack(player, plan);
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine)
                || source == null || !(source.getItem() instanceof IAmmo ammo)
                || !canLoad(magazine, carrier, source, ammo)) {
            return false;
        }
        Identifier roundAmmo = ammo.getAmmoId(source);
        boolean consumesSource = IGunOperator.fromLivingEntity(player).needCheckAmmo();
        // A direct inventory-slot source must use Slot#safeTake so result or
        // restricted container slots retain their own server validation.
        if (consumesSource && plan.getPlayerInventorySourceSlot() < 0) {
            Slot sourceSlot = plan.getInteractionSlot();
            ItemStack extracted = sourceSlot == null ? ItemStack.EMPTY : sourceSlot.safeTake(1, 1, player);
            if (extracted.isEmpty() || !magazine.pushRound(carrier, roundAmmo)) {
                if (!extracted.isEmpty() && sourceSlot != null) {
                    sourceSlot.safeInsert(extracted);
                }
                return false;
            }
        } else {
            if (!magazine.pushRound(carrier, roundAmmo)) {
                return false;
            }
            // Creative/free-ammo policy preserves timing and a visible selected
            // profile sample, but does not decrement its real source stack.
            if (consumesSource) {
                source.shrink(1);
                if (source.isEmpty()) {
                    clearSource(player, plan);
                } else {
                    markSourceChanged(player, plan);
                }
            }
        }
        syncCarrier(player, plan, carrier);
        player.playSound(net.minecraft.sounds.SoundEvents.BUNDLE_INSERT, 0.8F,
                0.85F + player.level().getRandom().nextFloat() * 0.25F);
        return true;
    }

    private static boolean completeUnload(Player player, InventoryRoundHandlingPlan plan) {
        ItemStack carrier = carrierStack(player, plan);
        Slot output = plan.getInteractionSlot();
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine) || output == null
                || !canUnload(magazine, carrier, output, plan.getOutputReference())) {
            return false;
        }
        ItemStack loose = AmmoItemBuilder.create().setId(magazine.getNextRoundAmmoId(carrier)).setCount(1).build();
        ItemStack remainder = output.safeInsert(loose);
        if (!remainder.isEmpty()) {
            return false;
        }
        if (magazine.popNextRound(carrier).equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return false;
        }
        if (plan.getOutputReference() == null) {
            plan.setOutputReference(output.getItem());
        }
        syncCarrier(player, plan, carrier);
        player.playSound(net.minecraft.sounds.SoundEvents.BUNDLE_REMOVE_ONE, 0.8F,
                0.85F + player.level().getRandom().nextFloat() * 0.25F);
        return true;
    }

    private static ItemStack carrierStack(Player player, InventoryRoundHandlingPlan plan) {
        Slot slot = plan.getCarrierSlot();
        return slot == null ? player.containerMenu.getCarried() : slot.getItem();
    }

    private static void syncCarrier(Player player, InventoryRoundHandlingPlan plan, ItemStack carrier) {
        Slot slot = plan.getCarrierSlot();
        if (slot == null) {
            player.containerMenu.setCarried(carrier);
        } else {
            slot.setChanged();
        }
    }

    private static ItemStack sourceStack(Player player, InventoryRoundHandlingPlan plan) {
        if (plan.getPlayerInventorySourceSlot() >= 0) {
            return player.getInventory().getItem(plan.getPlayerInventorySourceSlot());
        }
        Slot slot = plan.getInteractionSlot();
        return slot == null ? null : slot.getItem();
    }

    private static void clearSource(Player player, InventoryRoundHandlingPlan plan) {
        if (plan.getPlayerInventorySourceSlot() >= 0) {
            player.getInventory().setItem(plan.getPlayerInventorySourceSlot(), ItemStack.EMPTY);
            player.getInventory().setChanged();
            return;
        }
        Slot slot = plan.getInteractionSlot();
        if (slot != null) {
            slot.set(ItemStack.EMPTY);
        }
    }

    private static void markSourceChanged(Player player, InventoryRoundHandlingPlan plan) {
        if (plan.getPlayerInventorySourceSlot() >= 0) {
            player.getInventory().setChanged();
            return;
        }
        Slot slot = plan.getInteractionSlot();
        if (slot != null) {
            slot.setChanged();
        }
    }

    private static boolean canLoad(MagazineItemDataAccessor magazine, ItemStack carrier, ItemStack source, IAmmo ammo) {
        IdentifierPair ids = new IdentifierPair(magazine.getAmmoId(carrier), ammo.getAmmoId(source));
        return magazine.getAmmoCount(carrier) < magazine.getCapacity(carrier)
                && AmmoProfileService.isLoadedAmmoIdentity(ids.roundAmmo())
                && AmmoProfileService.isSameCaliber(ids.carrierCaliber(), ids.roundAmmo());
    }

    private static boolean canUnload(MagazineItemDataAccessor magazine, ItemStack carrier, Slot output,
                                     ItemStack expectedOutput) {
        if (magazine.getAmmoCount(carrier) <= 0) {
            return false;
        }
        if (!AmmoProfileService.isLoadedAmmoIdentity(magazine.getNextRoundAmmoId(carrier))) {
            return false;
        }
        ItemStack loose = AmmoItemBuilder.create().setId(magazine.getNextRoundAmmoId(carrier)).setCount(1).build();
        ItemStack current = output.getItem();
        if (expectedOutput == null) {
            return current.isEmpty();
        }
        return current == expectedOutput && ItemStack.isSameItemSameComponents(current, loose)
                && current.getCount() < current.getMaxStackSize();
    }

    private static int findCompatibleInventorySource(Player player, MagazineItemDataAccessor magazine, ItemStack carrier) {
        int slots = player.getInventory().getNonEquipmentItems().size();
        for (int index = 0; index < slots; index++) {
            ItemStack candidate = player.getInventory().getItem(index);
            if (candidate.getItem() instanceof IAmmo ammo && canLoad(magazine, carrier, candidate, ammo)) {
                return index;
            }
        }
        return -1;
    }

    private static String ensureCarrierIdentity(MagazineItemDataAccessor magazine, ItemStack carrier) {
        String identity = magazine.getRoundHandlingInstanceId(carrier);
        if (identity.isBlank()) {
            identity = UUID.randomUUID().toString();
            magazine.setRoundHandlingInstanceId(carrier, identity);
        }
        return identity;
    }

    private static int durationTicks(boolean loader, boolean load) {
        int base = load
                ? (SyncConfig.INDUSTRY_ROUND_LOAD_TICKS == null ? 10 : SyncConfig.INDUSTRY_ROUND_LOAD_TICKS.get())
                : (SyncConfig.INDUSTRY_ROUND_UNLOAD_TICKS == null ? 8 : SyncConfig.INDUSTRY_ROUND_UNLOAD_TICKS.get());
        if (!loader) {
            return base;
        }
        double multiplier = SyncConfig.INDUSTRY_MAGAZINE_LOADER_TIME_MULTIPLIER == null
                ? 0.75D : SyncConfig.INDUSTRY_MAGAZINE_LOADER_TIME_MULTIPLIER.get();
        return Math.max(4, (int) Math.ceil(base * multiplier));
    }

    private static boolean isFeatureEnabled() {
        return com.tacz.guns.industry.IndustryProfileManager.isCreateFlyProfileActive()
                && SyncConfig.PHYSICAL_MAGAZINES != null && SyncConfig.PHYSICAL_MAGAZINES.get();
    }

    private record IdentifierPair(net.minecraft.resources.Identifier carrierCaliber,
                                  net.minecraft.resources.Identifier roundAmmo) {
    }
}
