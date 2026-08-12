package me.xjqsh.lrtactical.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ThrowableTooltip(ItemStack stack) implements TooltipComponent {
}
