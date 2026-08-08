package com.tacz.guns.item;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.industry.ammo.AmmoProfileService;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Reusable inventory loading tool. Hold it on the cursor and secondary-click a
 * physical carrier to immediately transfer rounds from the first compatible
 * loose-ammo stack in the player's inventory.
 */
public final class MagazineLoaderItem extends Item {
    public MagazineLoaderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack loader, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack target = slot.getItem();
        if (!(target.getItem() instanceof MagazineItemDataAccessor magazine) || !magazine.isConfigured(target)) {
            return false;
        }
        // The authoritative server owns the inventory extraction. Returning
        // true on the client prevents vanilla from swapping the cursor tool
        // with the carrier before the server's slot update arrives.
        if (player.level().isClientSide()) {
            return true;
        }
        int inserted = loadFromPlayerInventory(player, target, magazine);
        if (inserted <= 0) {
            return false;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.9F,
                0.85F + player.level().getRandom().nextFloat() * 0.25F);
        return true;
    }

    private static int loadFromPlayerInventory(Player player, ItemStack carrierStack,
                                               MagazineItemDataAccessor carrier) {
        int free = carrier.getCapacity(carrierStack) - carrier.getAmmoCount(carrierStack);
        if (free <= 0) {
            return 0;
        }
        boolean consumesAmmo = IGunOperator.fromLivingEntity(player).needCheckAmmo();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!(candidate.getItem() instanceof IAmmo ammo) || !isCompatible(carrier, carrierStack, candidate, ammo)) {
                continue;
            }

            int wanted = consumesAmmo ? Math.min(free, candidate.getCount()) : free;
            Identifier roundAmmoId = ammo.getAmmoId(candidate);
            int inserted = 0;
            while (inserted < wanted && carrier.pushRound(carrierStack, roundAmmoId)) {
                inserted++;
            }
            if (inserted <= 0) {
                return 0;
            }
            if (consumesAmmo) {
                candidate.shrink(inserted);
                if (candidate.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
            return inserted;
        }
        return 0;
    }

    private static boolean isCompatible(MagazineItemDataAccessor carrier, ItemStack carrierStack,
                                        ItemStack source, IAmmo ammo) {
        Identifier roundAmmoId = ammo.getAmmoId(source);
        return AmmoProfileService.isLoadedAmmoIdentity(roundAmmoId)
                && AmmoProfileService.isSameCaliber(carrier.getAmmoId(carrierStack), roundAmmoId);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        adder.accept(Component.translatable("tooltip.tacz.magazine_loader.usage")
                .withStyle(style -> style.withColor(0x777777)));
    }
}
