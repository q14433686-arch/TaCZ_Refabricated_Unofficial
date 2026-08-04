package com.tacz.guns.industry.magazine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** API exposed by the physical magazine item. */
public interface IMagazine {
    String getMagazineFamily(ItemStack magazine);

    Identifier getAmmoId(ItemStack magazine);

    int getCapacity(ItemStack magazine);

    int getAmmoCount(ItemStack magazine);

    void setAmmoCount(ItemStack magazine, int count);

    String getDisplayNameKey(ItemStack magazine);

    boolean isConfigured(ItemStack magazine);
}
