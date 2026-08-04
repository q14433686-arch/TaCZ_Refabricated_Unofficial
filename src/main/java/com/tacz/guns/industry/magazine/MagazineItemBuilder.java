package com.tacz.guns.industry.magazine;

import com.tacz.guns.init.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Small, explicit factory for a configured physical magazine stack. */
public final class MagazineItemBuilder {
    private String family = "";
    private Identifier ammoId;
    private int capacity;
    private int ammoCount;
    private String displayName = "";

    private MagazineItemBuilder() {
    }

    public static MagazineItemBuilder create() {
        return new MagazineItemBuilder();
    }

    public MagazineItemBuilder setFamily(String family) {
        this.family = family == null ? "" : family;
        return this;
    }

    public MagazineItemBuilder setAmmoId(Identifier ammoId) {
        this.ammoId = ammoId;
        return this;
    }

    public MagazineItemBuilder setCapacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public MagazineItemBuilder setAmmoCount(int ammoCount) {
        this.ammoCount = ammoCount;
        return this;
    }

    public MagazineItemBuilder setDisplayNameKey(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
        return this;
    }

    public MagazineItemBuilder fromDefinition(GunFeedDefinition definition) {
        return setFamily(definition.getMagazineFamily())
                .setAmmoId(definition.getAmmoId())
                .setCapacity(definition.getMagazineCapacity())
                .setDisplayNameKey(definition.getDisplayName());
    }

    public ItemStack build() {
        ItemStack magazine = new ItemStack(ModItems.MAGAZINE);
        if (magazine.getItem() instanceof MagazineItemDataAccessor accessor) {
            accessor.setMagazineFamily(magazine, family);
            accessor.setAmmoId(magazine, ammoId);
            accessor.setCapacity(magazine, capacity);
            accessor.setAmmoCount(magazine, ammoCount);
            accessor.setDisplayNameKey(magazine, displayName);
        }
        return magazine;
    }
}
