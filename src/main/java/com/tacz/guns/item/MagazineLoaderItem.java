package com.tacz.guns.item;

import com.tacz.guns.industry.magazine.InventoryRoundHandlingService;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Reusable inventory loading tool. It keeps the familiar loader-on-magazine
 * interaction but moves one real round per timed server transaction.
 */
public final class MagazineLoaderItem extends Item {
    public MagazineLoaderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack loader, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY
                || !(slot.getItem().getItem() instanceof MagazineItemDataAccessor magazine)
                || !magazine.isConfigured(slot.getItem())) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        return InventoryRoundHandlingService.beginLoaderInteraction(player, loader, slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        adder.accept(Component.translatable("tooltip.tacz.magazine_loader.usage")
                .withStyle(style -> style.withColor(0x777777)));
    }
}
