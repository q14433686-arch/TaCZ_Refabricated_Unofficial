package com.tacz.guns.industry.magazine;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Persistent data carried by one physical magazine stack.
 *
 * <p>Magazine stacks never stack, so the count, compatibility family and
 * capacity travel together through inventories, containers, drops and player
 * trades.  The data intentionally stores a single ammo id: mixed ammunition
 * would make deterministic reload selection and HUD accounting needlessly
 * ambiguous in the first implementation.</p>
 */
public interface MagazineItemDataAccessor extends IMagazine {
    String MAGAZINE_FAMILY_TAG = "MagazineFamily";
    String MAGAZINE_AMMO_ID_TAG = "MagazineAmmoId";
    String MAGAZINE_CAPACITY_TAG = "MagazineCapacity";
    String MAGAZINE_AMMO_COUNT_TAG = "MagazineAmmoCount";
    String MAGAZINE_DISPLAY_NAME_TAG = "MagazineDisplayName";

    int MAX_MAGAZINE_CAPACITY = 512;

    @Override
    default String getMagazineFamily(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(MAGAZINE_FAMILY_TAG, "");
    }

    default void setMagazineFamily(ItemStack magazine, String family) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(MAGAZINE_FAMILY_TAG, family == null ? "" : family));
    }

    @Override
    default Identifier getAmmoId(ItemStack magazine) {
        CompoundTag tag = ItemNbtUtils.getTag(magazine);
        Identifier id = Identifier.tryParse(tag.getStringOr(MAGAZINE_AMMO_ID_TAG, ""));
        return id == null ? DefaultAssets.EMPTY_AMMO_ID : id;
    }

    default void setAmmoId(ItemStack magazine, @Nullable Identifier ammoId) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                MAGAZINE_AMMO_ID_TAG,
                ammoId == null ? DefaultAssets.EMPTY_AMMO_ID.toString() : ammoId.toString()
        ));
    }

    @Override
    default int getCapacity(ItemStack magazine) {
        return Math.clamp(
                ItemNbtUtils.getTag(magazine).getIntOr(MAGAZINE_CAPACITY_TAG, 0),
                0,
                MAX_MAGAZINE_CAPACITY
        );
    }

    default void setCapacity(ItemStack magazine, int capacity) {
        int safeCapacity = Math.clamp(capacity, 1, MAX_MAGAZINE_CAPACITY);
        ItemNbtUtils.updateTag(magazine, tag -> {
            tag.putInt(MAGAZINE_CAPACITY_TAG, safeCapacity);
            int oldCount = tag.getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0);
            tag.putInt(MAGAZINE_AMMO_COUNT_TAG, Math.clamp(oldCount, 0, safeCapacity));
        });
    }

    @Override
    default int getAmmoCount(ItemStack magazine) {
        return Math.clamp(
                ItemNbtUtils.getTag(magazine).getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0),
                0,
                getCapacity(magazine)
        );
    }

    @Override
    default void setAmmoCount(ItemStack magazine, int count) {
        int capacity = getCapacity(magazine);
        ItemNbtUtils.updateTag(magazine, tag -> tag.putInt(
                MAGAZINE_AMMO_COUNT_TAG,
                Math.clamp(count, 0, capacity)
        ));
    }

    @Override
    default String getDisplayNameKey(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(MAGAZINE_DISPLAY_NAME_TAG, "");
    }

    default void setDisplayNameKey(ItemStack magazine, String displayNameKey) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                MAGAZINE_DISPLAY_NAME_TAG,
                displayNameKey == null ? "" : displayNameKey
        ));
    }

    @Override
    default boolean isConfigured(ItemStack magazine) {
        return !getMagazineFamily(magazine).isBlank()
                && !getAmmoId(magazine).equals(DefaultAssets.EMPTY_AMMO_ID)
                && getCapacity(magazine) > 0;
    }
}
