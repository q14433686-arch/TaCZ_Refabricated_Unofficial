package com.tacz.guns.industry.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.industry.icon.IndustryIconRenderer;
import com.tacz.guns.industry.ammo.CartridgeStackLimitService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Generic rendered item for a platform-specific component or reusable blueprint.
 */
public class IndustryTaggedItem extends Item implements IndustryItemDataAccessor, IItem {
    public IndustryTaggedItem(Properties properties) {
        super(properties);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return IndustryIconRenderer.INSTANCE.get();
    }

    /** Normalize pre-existing configured cases/projectiles after a world update. */
    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull ServerLevel level,
                              @Nonnull Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        CartridgeStackLimitService.normalize(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String key = getDisplayNameKey(stack);
        return key.isBlank() ? super.getName(stack) : Component.translatable(key);
    }

    private static int blueprintTierColor(String tier) {
        return switch (tier) {
            case "legacy" -> 0xB8C7A0;
            case "service" -> 0x7FC6E8;
            case "advanced" -> 0xD4A85A;
            case "precision" -> 0xD07CEB;
            default -> 0xAAAAAA;
        };
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
            String tier = getBlueprintTier(stack);
            if (!tier.isBlank()) {
                adder.accept(Component.translatable("tooltip.tacz.blueprint.tier." + tier)
                        .withStyle(style -> style.withColor(blueprintTierColor(tier))));
            }
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
        if ("case".equals(partKind) || "spent_case".equals(partKind) || "projectile".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.stack_limit", stack.getMaxStackSize())
                    .withStyle(style -> style.withColor(0x777777)));
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
