package com.tacz.guns.industry.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.industry.icon.IndustryIconRenderer;
import com.tacz.guns.industry.ammo.CartridgeStackLimitService;
import com.tacz.guns.industry.blueprint.BlueprintKnowledge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    /**
     * Archive study is intentionally a physical, server-authoritative action:
     * hold the recovered dossier in the main hand and a book in the offhand.
     * The dossier becomes factory tooling only after the player records it.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!"blueprint".equals(getPartKind(stack)) || !BLUEPRINT_ARCHIVE.equals(getBlueprintState(stack))) {
            return InteractionResult.PASS;
        }
        ItemStack book = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!book.is(Items.BOOK)) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.tacz.blueprint.need_book"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        String platform = getPlatform(stack);
        if (platform.isBlank()) {
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            book.shrink(1);
        }
        setBlueprintState(stack, BLUEPRINT_PRODUCTION);
        boolean newKnowledge = BlueprintKnowledge.learn(serverPlayer, platform);
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.translatable(newKnowledge
                ? "message.tacz.blueprint.studied"
                : "message.tacz.blueprint.transcribed", getName(stack)), true);
        return InteractionResult.CONSUME;
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
            if (BLUEPRINT_PRODUCTION.equals(getBlueprintState(stack))) {
                adder.accept(Component.translatable("tooltip.tacz.blueprint.production")
                        .withStyle(style -> style.withColor(0x55FFFF)));
            } else {
                adder.accept(Component.translatable("tooltip.tacz.blueprint.archive")
                        .withStyle(style -> style.withColor(0xFFCC66)));
                adder.accept(Component.translatable("tooltip.tacz.blueprint.study")
                        .withStyle(style -> style.withColor(0xAAAAAA)));
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
