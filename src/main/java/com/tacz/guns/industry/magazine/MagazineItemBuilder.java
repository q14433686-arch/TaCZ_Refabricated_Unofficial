package com.tacz.guns.industry.magazine;

import com.tacz.guns.industry.ammo.AmmoProfileService;
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
    private String feedDeviceKind = "";

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

    public MagazineItemBuilder setFeedDeviceKind(String feedDeviceKind) {
        this.feedDeviceKind = feedDeviceKind == null ? "" : feedDeviceKind;
        return this;
    }

    public MagazineItemBuilder fromDefinition(GunFeedDefinition definition) {
        int capacity = definition.getMechanism().usesPhysicalFeedDevice()
                ? definition.getFeedDeviceCapacity() : definition.getMagazineCapacity();
        return setFamily(definition.getMagazineFamily())
                .setAmmoId(definition.getAmmoId())
                .setCapacity(capacity)
                .setDisplayNameKey(definition.getDisplayName())
                .setFeedDeviceKind(definition.getMechanism().serializedName());
    }

    /**
     * Build one declared detachable-magazine/belt capacity. The variant is
     * supplied by a validated {@link GunFeedDefinition}; this factory does not
     * manufacture an arbitrary larger NBT capacity from a loose round count.
     */
    public MagazineItemBuilder fromExternalCarrier(GunFeedDefinition definition, ExternalCarrierVariant variant) {
        if (variant == null) {
            return fromDefinition(definition);
        }
        return setFamily(definition.getMagazineFamily())
                // External-carrier stacks store the shared canonical cartridge
                // specification. The receiver still validates its exact
                // GunData ammo in GunFeedDefinition; this only lets explicitly
                // shared standards span reviewed native AmmoId aliases.
                .setAmmoId(AmmoProfileService.canonicalCaliber(definition.getAmmoId()))
                .setCapacity(variant.getCapacity())
                .setDisplayNameKey(variant.getDisplayName())
                .setFeedDeviceKind(definition.getMechanism().serializedName());
    }

    public ItemStack build() {
        ItemStack magazine = new ItemStack(ModItems.MAGAZINE);
        if (magazine.getItem() instanceof MagazineItemDataAccessor accessor) {
            accessor.setMagazineFamily(magazine, family);
            accessor.setAmmoId(magazine, ammoId);
            accessor.setCapacity(magazine, capacity);
            accessor.setAmmoCount(magazine, ammoCount);
            accessor.setDisplayNameKey(magazine, displayName);
            accessor.setFeedDeviceKind(magazine, feedDeviceKind);
        }
        return magazine;
    }
}
