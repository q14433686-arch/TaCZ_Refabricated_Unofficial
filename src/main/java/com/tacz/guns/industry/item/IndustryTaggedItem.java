package com.tacz.guns.industry.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Generic rendered item for a platform-specific component or reusable blueprint.
 */
public class IndustryTaggedItem extends Item implements IndustryItemDataAccessor {
    public IndustryTaggedItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String key = getDisplayNameKey(stack);
        return key.isBlank() ? super.getName(stack) : Component.translatable(key);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        if (!isConfiguredIndustryPart(stack)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.unconfigured")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }
        String partKind = getPartKind(stack);
        if ("blueprint".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.gun_smith_table.non_consumed")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if (partKind.endsWith("_die")) {
            adder.accept(Component.translatable("tooltip.tacz.industry.reusable_die")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("cartridge_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.cartridge_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("spent_case".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.spent_case")
                    .withStyle(style -> style.withColor(0xFFCC66)));
        }
        if (!getCartridgeCaliber(stack).isBlank()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.caliber", getCartridgeCaliber(stack))
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if (!getProjectileType(stack).isBlank()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.projectile_type", getProjectileType(stack))
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if (!getDieTargetKind(stack).isBlank()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.die_target", getDieTargetKind(stack))
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if (advanced.isAdvanced()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.platform", getPlatform(stack))
                    .withStyle(style -> style.withColor(0x555555)));
            adder.accept(Component.translatable("tooltip.tacz.industry.part", getPartKind(stack))
                    .withStyle(style -> style.withColor(0x555555)));
        }
    }
}
