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
    /** detachable_magazine/belt carriers; stripper_clip/speedloader loaders; en_bloc_clip is installed in gun NBT. */
    String FEED_DEVICE_KIND_TAG = "FeedDeviceKind";
    /**
     * Stable per-stack identity used only while a bridge clip/speedloader is
     * reserved during a reload animation. AmmoCount is intentionally mutable,
     * so comparing a copied ItemStack byte-for-byte would reject the second
     * round of a genuine scripted loop.
     */
    String FEED_DEVICE_INSTANCE_ID_TAG = "FeedDeviceInstanceId";

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

    /**
     * Existing magazine stacks predate this tag. Their behaviour is still
     * resolved by the gun's FeedMechanism, so the neutral default here is only
     * a display/storage convention and never changes an old belt box into an
     * AR magazine by itself.
     */
    default String getFeedDeviceKind(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(FEED_DEVICE_KIND_TAG, "");
    }

    default void setFeedDeviceKind(ItemStack magazine, String kind) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                FEED_DEVICE_KIND_TAG, kind == null ? "" : kind
        ));
    }

    default String getFeedDeviceInstanceId(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(FEED_DEVICE_INSTANCE_ID_TAG, "");
    }

    default void setFeedDeviceInstanceId(ItemStack magazine, String instanceId) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                FEED_DEVICE_INSTANCE_ID_TAG, instanceId == null ? "" : instanceId
        ));
    }

    @Override
    default boolean isConfigured(ItemStack magazine) {
        return !getMagazineFamily(magazine).isBlank()
                && !getAmmoId(magazine).equals(DefaultAssets.EMPTY_AMMO_ID)
                && getCapacity(magazine) > 0;
    }
}
