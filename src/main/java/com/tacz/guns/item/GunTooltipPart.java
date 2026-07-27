package com.tacz.guns.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public enum GunTooltipPart {
    DESCRIPTION,
    AMMO_INFO,
    BASE_INFO,
    EXTRA_DAMAGE_INFO,
    UPGRADES_TIP,
    PACK_INFO;

    private final int mask = 1 << this.ordinal();

    public int getMask() {
        return this.mask;
    }

    public static int getHideFlags(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("HideFlags")) {
                return tag.getIntOr("HideFlags", 0);
            }
        }
        return /*stack.getItem().getDefaultTooltipHideFlags(stack)*/ 0;
    }

    public static void setHideFlags(ItemStack stack, int mask) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("HideFlags", mask));
    }
}
