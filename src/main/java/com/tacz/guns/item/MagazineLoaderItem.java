package com.tacz.guns.item;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.industry.magazine.IMagazine;
import net.minecraft.network.chat.Component;
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
 * Reusable speed-loader tool. Hold it on the cursor and secondary-click a
 * physical magazine to transfer matching loose rounds from the player's main
 * inventory in one server-authoritative operation.
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
        if (!(target.getItem() instanceof IMagazine magazine) || !magazine.isConfigured(target)) {
            return false;
        }
        // Let the authoritative server perform inventory extraction. Returning
        // true here prevents ordinary slot swapping while waiting for sync.
        if (player.level().isClientSide()) {
            return true;
        }
        int inserted = loadFromPlayerInventory(player, target, magazine);
        if (inserted > 0) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.9F, 0.85F + player.level().getRandom().nextFloat() * 0.25F);
            return true;
        }
        return false;
    }

    private static int loadFromPlayerInventory(Player player, ItemStack magazineStack, IMagazine magazine) {
        int free = magazine.getCapacity(magazineStack) - magazine.getAmmoCount(magazineStack);
        if (free <= 0) {
            return 0;
        }
        // Creative follows TACZ's central reload rule: only grant free rounds
        // when the server says this player does not need real ammo.
        if (!IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
            magazine.setAmmoCount(magazineStack, magazine.getCapacity(magazineStack));
            return free;
        }
        int remaining = free;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && remaining > 0; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!(candidate.getItem() instanceof IAmmo ammo) || !magazine.getAmmoId(magazineStack).equals(ammo.getAmmoId(candidate))) {
                continue;
            }
            int take = Math.min(remaining, candidate.getCount());
            candidate.shrink(take);
            if (candidate.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= take;
        }
        int inserted = free - remaining;
        if (inserted > 0) {
            magazine.setAmmoCount(magazineStack, magazine.getAmmoCount(magazineStack) + inserted);
        }
        return inserted;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        adder.accept(Component.translatable("tooltip.tacz.magazine_loader.usage")
                .withStyle(style -> style.withColor(0x777777)));
    }
}
