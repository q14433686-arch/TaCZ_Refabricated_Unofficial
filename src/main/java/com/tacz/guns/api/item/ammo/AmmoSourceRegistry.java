package com.tacz.guns.api.item.ammo;

import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Registry and common entry point for entity ammunition sources.
 *
 * <p>Downstream mods can register a provider with {@link #EVENT}. If no provider accepts an entity,
 * TaCZ falls back to the entity's normal {@code tacz$getItemHandler} inventory. Register providers
 * during common mod initialization so client-side animation checks and server-side consumption use
 * the same source.</p>
 */
public final class AmmoSourceRegistry {
    /**
     * Custom source providers. Providers are queried in registration order; the first non-null
     * result is used instead of TaCZ's normal entity inventory.
     */
    public static final Event<AmmoSourceProvider> EVENT = EventFactory.createArrayBacked(
            AmmoSourceProvider.class,
            providers -> (shooter, gunItem) -> {
                for (AmmoSourceProvider provider : providers) {
                    AmmoSource source = provider.findAmmoSource(shooter, gunItem);
                    if (source != null) {
                        return source;
                    }
                }
                return null;
            }
    );

    private static final AmmoSource ENTITY_INVENTORY = new AmmoSource() {
        @Override
        public boolean hasAmmo(LivingEntity shooter, ItemStack gunItem) {
            return shooter.tacz$getItemHandler(null)
                    .map(itemHandler -> AmmoSourceRegistry.hasAmmo(itemHandler, gunItem))
                    .orElse(false);
        }

        @Override
        public int consumeAmmo(LivingEntity shooter, ItemStack gunItem, int requestedAmount) {
            return shooter.tacz$getItemHandler(null)
                    .map(itemHandler -> AmmoSourceRegistry.consumeAmmo(itemHandler, gunItem, requestedAmount))
                    .orElse(0);
        }
    };

    private AmmoSourceRegistry() {
    }

    /**
     * Resolves the source used for this entity and gun. This method never returns {@code null}.
     */
    public static AmmoSource getAmmoSource(LivingEntity shooter, ItemStack gunItem) {
        AmmoSource source = EVENT.invoker().findAmmoSource(shooter, gunItem);
        return source != null ? source : ENTITY_INVENTORY;
    }

    /**
     * Checks the registered source, falling back to the entity's normal inventory.
     */
    public static boolean hasAmmo(LivingEntity shooter, ItemStack gunItem) {
        return getAmmoSource(shooter, gunItem).hasAmmo(shooter, gunItem);
    }

    /**
     * Consumes ammunition from the registered source, falling back to the entity's normal
     * inventory. Invalid provider return values are clamped to the requested range.
     */
    public static int consumeAmmo(LivingEntity shooter, ItemStack gunItem, int requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }
        int consumed = getAmmoSource(shooter, gunItem).consumeAmmo(shooter, gunItem, requestedAmount);
        return Math.max(0, Math.min(consumed, requestedAmount));
    }

    /**
     * TaCZ's standard read-only ammunition scan for an item handler.
     */
    public static boolean hasAmmo(IItemHandler itemHandler, ItemStack gunItem) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack ammoStack = itemHandler.getStackInSlot(i);
            if (ammoStack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gunItem, ammoStack)) {
                return true;
            }
            if (ammoStack.getItem() instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(gunItem, ammoStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * TaCZ's standard extraction algorithm for an item handler.
     */
    public static int consumeAmmo(IItemHandler itemHandler, ItemStack gunItem, int requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }
        int remaining = requestedAmount;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack ammoStack = itemHandler.getStackInSlot(i);
            if (ammoStack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gunItem, ammoStack)) {
                ItemStack extracted = itemHandler.extractItem(i, remaining, false);
                remaining -= extracted.getCount();
                if (remaining <= 0) {
                    break;
                }
            }
            if (ammoStack.getItem() instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(gunItem, ammoStack)) {
                int boxAmmoCount = ammoBox.getAmmoCount(ammoStack);
                int extractCount = Math.min(boxAmmoCount, remaining);
                int remainCount = boxAmmoCount - extractCount;
                ammoBox.setAmmoCount(ammoStack, remainCount);
                if (remainCount <= 0) {
                    ammoBox.setAmmoId(ammoStack, DefaultAssets.EMPTY_AMMO_ID);
                }
                remaining -= extractCount;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        return requestedAmount - remaining;
    }
}
