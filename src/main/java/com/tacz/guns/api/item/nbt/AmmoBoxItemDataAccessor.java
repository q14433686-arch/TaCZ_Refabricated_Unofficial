package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface AmmoBoxItemDataAccessor extends IAmmoBox {
    String AMMO_ID_TAG = "AmmoId";
    String AMMO_COUNT_TAG = "AmmoCount";
    String CREATIVE_TAG = "Creative";
    String ALL_TYPE_CREATIVE_TAG = "AllTypeCreative";
    String LEVEL_TAG = "Level";

    @Override
    default Identifier getAmmoId(ItemStack ammoBox) {
        CompoundTag tag = ItemNbtUtils.getTag(ammoBox);
        if (tag.contains(AMMO_ID_TAG)) {
            return Identifier.parse(tag.getStringOr(AMMO_ID_TAG, ""));
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammoBox, Identifier ammoId) {
        ItemNbtUtils.updateTag(ammoBox, tag -> tag.putString(AMMO_ID_TAG, ammoId.toString()));
    }

    @Override
    default int getAmmoCount(ItemStack ammoBox) {
        CompoundTag tag = ItemNbtUtils.getTag(ammoBox);
        if (isAllTypeCreative(ammoBox) || isCreative(ammoBox)) {
            return Integer.MAX_VALUE;
        }
        if (tag.contains(AMMO_COUNT_TAG)) {
            return tag.getIntOr(AMMO_COUNT_TAG, 0);
        }
        return 0;
    }

    @Override
    default void setAmmoCount(ItemStack ammoBox, int count) {
        ItemNbtUtils.updateTag(ammoBox, tag -> {
            if (isCreative(ammoBox)) {
                tag.putInt(AMMO_COUNT_TAG, Integer.MAX_VALUE);
            } else {
                tag.putInt(AMMO_COUNT_TAG, count);
            }
        });
    }

    @Override
    default boolean isAmmoBoxOfGun(ItemStack gun, ItemStack ammoBox) {
        if (gun.getItem() instanceof IGun iGun && ammoBox.getItem() instanceof IAmmoBox iAmmoBox) {
            if (isAllTypeCreative(ammoBox)) {
                return true;
            }
            Identifier ammoId = iAmmoBox.getAmmoId(ammoBox);
            if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            Identifier gunId = iGun.getGunId(gun);
            return TimelessAPI.getCommonGunIndex(gunId).map(gunIndex -> gunIndex.getGunData().getAmmoId().equals(ammoId)).orElse(false);
        }
        return false;
    }

    @Override
    default ItemStack setAmmoLevel(ItemStack ammoBox, int level) {
        ItemNbtUtils.updateTag(ammoBox, tag -> tag.putInt(LEVEL_TAG, Math.max(level, 0)));
        return ammoBox;
    }

    @Override
    default int getAmmoLevel(ItemStack ammoBox) {
        CompoundTag tag = ItemNbtUtils.getTag(ammoBox);
        if (tag.contains(LEVEL_TAG)) {
            return tag.getIntOr(LEVEL_TAG, 0);
        }
        return 0;
    }

    @Override
    default boolean isCreative(ItemStack ammoBox) {
        CompoundTag tag = ItemNbtUtils.getTag(ammoBox);
        if (tag.contains(CREATIVE_TAG)) {
            return tag.getBooleanOr(CREATIVE_TAG, false);
        }
        return false;
    }

    @Override
    default boolean isAllTypeCreative(ItemStack ammoBox) {
        CompoundTag tag = ItemNbtUtils.getTag(ammoBox);
        if (tag.contains(ALL_TYPE_CREATIVE_TAG)) {
            return tag.getBooleanOr(ALL_TYPE_CREATIVE_TAG, false);
        }
        return false;
    }

    @Override
    default ItemStack setCreative(ItemStack ammoBox, boolean isAllType) {
        ItemNbtUtils.updateTag(ammoBox, tag -> {
            if (isAllType) {
                if (tag.contains(CREATIVE_TAG)) {
                    tag.remove(CREATIVE_TAG);
                }
                tag.putBoolean(ALL_TYPE_CREATIVE_TAG, true);
            } else {
                if (tag.contains(ALL_TYPE_CREATIVE_TAG)) {
                    tag.remove(ALL_TYPE_CREATIVE_TAG);
                }
                tag.putBoolean(CREATIVE_TAG, true);
            }
        });
        return ammoBox;
    }
}
