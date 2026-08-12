package me.xjqsh.lrtactical.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record MeleeTooltip(ItemStack stack) implements TooltipComponent {
}
