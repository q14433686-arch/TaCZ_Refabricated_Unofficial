package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;

/**
 * One explicitly manufactured capacity of an external detachable magazine or
 * belt/ammunition-box carrier.
 *
 * <p>The base capacity remains {@code GunFeedDefinition.magazine_capacity}.
 * Entries in {@code carrier_variants} are only for additional capacities that
 * the currently loaded {@code GunData.extended_mag_ammo_amount} actually
 * exposes. They never infer that an arbitrary same-calibre magazine fits a
 * receiver.</p>
 */
public final class ExternalCarrierVariant {
    @SerializedName("capacity")
    private int capacity;

    /** Translation key saved onto this particular physical carrier stack. */
    @SerializedName("display_name")
    private String displayName = "";

    /** Gson constructor. */
    public ExternalCarrierVariant() {
    }

    /** Runtime base/variant factory; the data-pack form still uses Gson fields. */
    public ExternalCarrierVariant(int capacity, String displayName) {
        this.capacity = capacity;
        this.displayName = displayName;
    }

    public int getCapacity() {
        return Math.clamp(capacity, 0, MagazineItemDataAccessor.MAX_MAGAZINE_CAPACITY);
    }

    public String getDisplayName() {
        return displayName == null ? "" : displayName;
    }

    public boolean isValid() {
        return getCapacity() > 0 && !getDisplayName().isBlank();
    }
}
