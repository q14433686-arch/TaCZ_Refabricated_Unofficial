package com.tacz.guns.item;

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
 * Reusable loading tool for the ammunition handling bench. It reduces the
 * timed per-round handling duration but never moves a whole stack instantly.
 */
public final class MagazineLoaderItem extends Item {
    public MagazineLoaderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack loader, Slot slot, ClickAction action, Player player) {
        // The former inventory-click batch transfer bypassed mixed-round order,
        // per-round time, and output checks. Keep normal inventory movement;
        // the real tool effect is applied only in the bench tool slot.
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        adder.accept(Component.translatable("tooltip.tacz.magazine_loader.usage")
                .withStyle(style -> style.withColor(0x777777)));
    }
}
