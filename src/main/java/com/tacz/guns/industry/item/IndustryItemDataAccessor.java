package com.tacz.guns.industry.item;

import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Shared custom-data layout for the data-driven gun components and blueprints.
 *
 * <p>Components are intentionally one native item type plus a platform/kind
 * identity, rather than dozens of registry items.  This gives gun packs an
 * extension point without forcing every pack to add Java registrations, while
 * still allowing {@code forge:partial_nbt} ingredients to enforce exact
 * platform parts at final assembly.</p>
 */
public interface IndustryItemDataAccessor {
    String PLATFORM_TAG = "IndustryPlatform";
    String PART_KIND_TAG = "IndustryPartKind";
    String DISPLAY_NAME_TAG = "IndustryDisplayName";
    /** A case fixes the cartridge family/calibre used by the final loading recipe. */
    String CARTRIDGE_CALIBER_TAG = "CartridgeCaliber";
    /** Links a configured case/projectile to the loose-ammo stack limit it must use. */
    String CARTRIDGE_AMMO_ID_TAG = "CartridgeAmmoId";
    /** A projectile core fixes construction/type (FMJ, shot, etc.) independently of count. */
    String PROJECTILE_TYPE_TAG = "ProjectileType";
    /** Component dies declare the exact output component kind they can form. */
    String DIE_TARGET_KIND_TAG = "DieTargetKind";

    default String getPlatform(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(PLATFORM_TAG, "");
    }

    default void setPlatform(ItemStack stack, String platform) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(PLATFORM_TAG, platform == null ? "" : platform));
    }

    default String getPartKind(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(PART_KIND_TAG, "");
    }

    default void setPartKind(ItemStack stack, String kind) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(PART_KIND_TAG, kind == null ? "" : kind));
    }

    default String getDisplayNameKey(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(DISPLAY_NAME_TAG, "");
    }

    default void setDisplayNameKey(ItemStack stack, String key) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(DISPLAY_NAME_TAG, key == null ? "" : key));
    }

    default String getCartridgeCaliber(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(CARTRIDGE_CALIBER_TAG, "");
    }

    default void setCartridgeCaliber(ItemStack stack, String caliber) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(CARTRIDGE_CALIBER_TAG, caliber == null ? "" : caliber));
    }

    default String getCartridgeAmmoId(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(CARTRIDGE_AMMO_ID_TAG, "");
    }

    default void setCartridgeAmmoId(ItemStack stack, String ammoId) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(CARTRIDGE_AMMO_ID_TAG, ammoId == null ? "" : ammoId));
    }

    default String getProjectileType(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(PROJECTILE_TYPE_TAG, "");
    }

    default void setProjectileType(ItemStack stack, String projectileType) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(PROJECTILE_TYPE_TAG, projectileType == null ? "" : projectileType));
    }

    default String getDieTargetKind(ItemStack stack) {
        return ItemNbtUtils.getTag(stack).getStringOr(DIE_TARGET_KIND_TAG, "");
    }

    default void setDieTargetKind(ItemStack stack, String targetKind) {
        ItemNbtUtils.updateTag(stack, tag -> tag.putString(DIE_TARGET_KIND_TAG, targetKind == null ? "" : targetKind));
    }

    default boolean isConfiguredIndustryPart(ItemStack stack) {
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        return !tag.getStringOr(PLATFORM_TAG, "").isBlank()
                && !tag.getStringOr(PART_KIND_TAG, "").isBlank();
    }
}
