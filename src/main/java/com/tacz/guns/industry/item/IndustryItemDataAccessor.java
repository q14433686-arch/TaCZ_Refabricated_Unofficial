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

    default boolean isConfiguredIndustryPart(ItemStack stack) {
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        return !tag.getStringOr(PLATFORM_TAG, "").isBlank()
                && !tag.getStringOr(PART_KIND_TAG, "").isBlank();
    }
}
