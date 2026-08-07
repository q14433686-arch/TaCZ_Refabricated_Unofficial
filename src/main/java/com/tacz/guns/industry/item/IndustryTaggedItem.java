package com.tacz.guns.industry.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.industry.icon.IndustryIconRenderer;
import com.tacz.guns.industry.ammo.CartridgeStackLimitService;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.industry.service.IndustrialServiceBenchService;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.util.ItemNbtUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Generic rendered item for a platform-specific component, removable-carrier subassembly, or reusable blueprint.
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
        // Components dismantled by pre-service-component builds shared the
        // normal production item id. Move those condition-bearing legacy stacks
        // into the dedicated service registry item the first time a player has
        // them in inventory, so they cannot enter ordinary gun assembly routes.
        if (stack.is(ModItems.GUN_COMPONENT) && ItemNbtUtils.getTag(stack).contains("IndustryPartCondition")
                && entity instanceof Player player) {
            var inventory = player.getInventory();
            for (int index = 0; index < inventory.getNonEquipmentItems().size(); index++) {
                if (inventory.getItem(index) == stack) {
                    inventory.setItem(index, IndustrialServiceBenchService.toServiceComponent(stack));
                    inventory.setChanged();
                    break;
                }
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        String key = getDisplayNameKey(stack);
        Component base = key.isBlank() ? super.getName(stack) : Component.translatable(key);
        // Runtime-generated surveyed gun dossiers/templates/kits intentionally
        // share one generic localized name, so append their exact GunId for
        // searchable selection. Surveyed ammunition is different: its AmmoIndex
        // already supplies the actual player-facing product name, which is far
        // more useful than exposing an internal surveyed/<namespace>/<path>
        // identity in every case/projectile/gauge title.
        var data = ItemNbtUtils.getTag(stack);
        String surveyedGun = data.getStringOr("IndustrySurveyGunId", "");
        String surveyedAmmo = data.getStringOr("IndustrySurveyAmmoId", "");
        String surveyedAmmoName = data.getStringOr("IndustrySurveyAmmoName", "");
        if (!surveyedAmmoName.isBlank()) {
            return Component.translatable(surveyedAmmoName)
                    .append(Component.literal(" · ").withStyle(style -> style.withColor(0x777777)))
                    .append(base);
        }
        String surveyedTarget = !surveyedGun.isBlank() ? surveyedGun : surveyedAmmo;
        return surveyedTarget.isBlank() ? base : base.copy().append(Component.literal(" [" + surveyedTarget + "]")
                .withStyle(style -> style.withColor(0x777777)));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).contains("IndustryPartCondition");
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int condition = Math.clamp(ItemNbtUtils.getTag(stack).getIntOr("IndustryPartCondition", 10_000), 0, 10_000);
        return Math.clamp(1 + condition * 12 / 10_000, 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int condition = Math.clamp(ItemNbtUtils.getTag(stack).getIntOr("IndustryPartCondition", 10_000), 0, 10_000);
        return Mth.hsvToRgb(condition * 0.33F / 10_000F, 0.92F, 0.95F);
    }

    private static int conditionColor(int condition) {
        return Mth.hsvToRgb(Math.clamp(condition, 0, 10_000) * 0.33F / 10_000F, 0.92F, 0.95F);
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
        if ("blueprint".equals(partKind) || "template_blank".equals(partKind)) {
            String role = getBlueprintRole(stack);
            if ("template_blank".equals(partKind) || "blank".equals(role)) {
                adder.accept(Component.translatable("tooltip.tacz.blueprint.role.blank")
                        .withStyle(style -> style.withColor(0xAAAAAA)));
            } else {
                adder.accept(Component.translatable("tooltip.tacz.gun_smith_table.non_consumed")
                        .withStyle(style -> style.withColor(0x55FFFF)));
                String roleKey = role.isBlank() ? "legacy" : role;
                adder.accept(Component.translatable("tooltip.tacz.blueprint.role." + roleKey)
                        .withStyle(style -> style.withColor("master".equals(role) ? 0xD4A85A : 0x8FD6C6)));
            }
            String tier = getBlueprintTier(stack);
            if (!tier.isBlank()) {
                adder.accept(Component.translatable("tooltip.tacz.blueprint.tier." + tier)
                        .withStyle(style -> style.withColor(blueprintTierColor(tier))));
            }
            String actionProfile = getActionProfile(stack);
            if (!actionProfile.isBlank()) {
                adder.accept(Component.translatable("tooltip.tacz.industry.action_profile",
                                Component.translatable("tooltip.tacz.industry.action_profile." + actionProfile))
                        .withStyle(style -> style.withColor(0x8AA7B7)));
            }
            String toolingScope = getToolingScope(stack);
            if (!toolingScope.isBlank()) {
                adder.accept(Component.translatable("tooltip.tacz.industry.tooling_scope",
                                Component.translatable("tooltip.tacz.industry.tooling_scope." + toolingScope))
                        .withStyle(style -> style.withColor(0x8AA7B7)));
            }
        }
        if (partKind.endsWith("_die")) {
            adder.accept(Component.translatable("tooltip.tacz.industry.reusable_die")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("action_jig".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.action_jig")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("critical_fit_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.critical_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("acceptance_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.acceptance_gauge")
                    .withStyle(style -> style.withColor(0xD4A85A)));
        }
        if ("cartridge_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.cartridge_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("case_datum_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.case_datum_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("projectile_datum_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.projectile_datum_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("cartridge_gauge_blank".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.cartridge_gauge_blank")
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if ("carrier_gauge_blank".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.carrier_gauge_blank")
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if ("carrier_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.carrier_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("carrier_body".equals(partKind) || "carrier_feed_kit".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.carrier_component")
                    .withStyle(style -> style.withColor(0x8FD6C6)));
        }
        if (partKind.startsWith("carrier_")) {
            CompoundTag tag = ItemNbtUtils.getTag(stack);
            String family = tag.getStringOr(MagazineItemDataAccessor.MAGAZINE_FAMILY_TAG, "");
            String ammo = tag.getStringOr(MagazineItemDataAccessor.MAGAZINE_AMMO_ID_TAG, "");
            int capacity = Math.max(0, tag.getIntOr(MagazineItemDataAccessor.MAGAZINE_CAPACITY_TAG, 0));
            if (!family.isBlank() && !ammo.isBlank() && capacity > 0) {
                adder.accept(Component.translatable("tooltip.tacz.industry.carrier_spec", family, ammo, capacity)
                        .withStyle(style -> style.withColor(0xAAAAAA)));
            }
        }
        if ("survey_archive".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.survey_archive")
                    .withStyle(style -> style.withColor(0xD4A85A)));
        }
        if ("survey_fixture".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.survey_fixture")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        if ("surveyed_platform_kit".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.surveyed_platform_kit")
                    .withStyle(style -> style.withColor(0x8FD6C6)));
        }
        if ("survey_cartridge_gauge".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.survey_cartridge_gauge")
                    .withStyle(style -> style.withColor(0x55FFFF)));
        }
        CompoundTag surveyTag = ItemNbtUtils.getTag(stack);
        String surveyedGun = surveyTag.getStringOr("IndustrySurveyGunId", "");
        String surveyedAmmo = surveyTag.getStringOr("IndustrySurveyAmmoId", "");
        String surveyedTarget = !surveyedGun.isBlank() ? surveyedGun : surveyedAmmo;
        if (!surveyedTarget.isBlank()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.surveyed_target", surveyedTarget)
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if (!surveyedAmmo.isBlank() && ("case".equals(partKind) || "projectile".equals(partKind))) {
            adder.accept(Component.translatable("tooltip.tacz.industry.surveyed_cartridge_part")
                    .withStyle(style -> style.withColor(0x8FD6C6)));
        }
        if (partKind.startsWith("dossier_archive_")) {
            adder.accept(Component.translatable("tooltip.tacz.industry.dossier_archive")
                    .withStyle(style -> style.withColor(0xD4A85A)));
        }
        if ("spent_case".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.spent_case")
                    .withStyle(style -> style.withColor(0xFFCC66)));
        }
        if ("motor_housing".equals(partKind)) {
            adder.accept(Component.translatable("tooltip.tacz.industry.motor_housing")
                    .withStyle(style -> style.withColor(0x8FD6C6)));
        }
        if (!getCartridgeCaliber(stack).isBlank()) {
            adder.accept(Component.translatable("tooltip.tacz.industry.caliber", getCartridgeCaliber(stack))
                    .withStyle(style -> style.withColor(0xAAAAAA)));
        }
        if ("case".equals(partKind) || "motor_housing".equals(partKind)
                || "spent_case".equals(partKind) || "projectile".equals(partKind)) {
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

        // B.2 service parts must be self-explanatory in inventory/REI: the
        // player should never need to inspect hidden NBT to learn whether this
        // is a damaged component, a neutral blank, or the named replacement.
        CompoundTag serviceTag = ItemNbtUtils.getTag(stack);
        if (serviceTag.contains("IndustryPartCondition")) {
            int condition = Math.clamp(serviceTag.getIntOr("IndustryPartCondition", 10_000), 0, 10_000);
            String conditionText = String.format(java.util.Locale.ROOT, "%.2f%%", condition / 100.0D);
            adder.accept(Component.translatable("tooltip.tacz.service.component_condition", conditionText)
                    .withStyle(style -> style.withColor(conditionColor(condition))));
            String serviceGun = serviceTag.getStringOr("IndustryServiceGunId", "");
            if (!serviceGun.isBlank()) {
                adder.accept(Component.translatable("tooltip.tacz.service.component_gun", serviceGun)
                        .withStyle(style -> style.withColor(0x8AA7B7)));
            }
            adder.accept(Component.translatable("tooltip.tacz.service.component_repair")
                    .withStyle(style -> style.withColor(0xF2C14E)));
        } else if ("service_part_blank".equals(partKind) || partKind.startsWith("service_part_")) {
            // Kept registered solely so worlds made during the first B.2 pass
            // do not lose items. New repairs are intentionally bench-native.
            adder.accept(Component.translatable("tooltip.tacz.service.legacy_part")
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
