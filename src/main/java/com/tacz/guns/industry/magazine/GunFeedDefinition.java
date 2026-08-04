package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

/**
 * Data-driven physical-feed declaration for one gun id.
 *
 * <p>Files live in {@code data/<namespace>/industry/gun_feed/<gun-path>.json}.
 * Their resource id is the gun id, avoiding an extra string field that could
 * disagree with the file name.  Third-party gun packs can opt in by supplying
 * their own declaration; packs without one retain their legacy behaviour.</p>
 */
public class GunFeedDefinition {
    @SerializedName("mechanism")
    private FeedMechanism mechanism = FeedMechanism.LEGACY;

    /** Cross-platform compatibility key such as {@code ak_762x39} or {@code stanag_556}. */
    @SerializedName("magazine_family")
    private String magazineFamily = "";

    /** Maximum capacity that this receiver accepts.  Smaller compatible magazines remain valid. */
    @SerializedName("magazine_capacity")
    private int magazineCapacity = 0;

    /** The only loose-ammo type accepted by magazines made from this definition. */
    @SerializedName("ammo")
    private Identifier ammoId = null;

    /** Translation key saved onto a physical magazine for stable tooltips after network sync. */
    @SerializedName("display_name")
    private String displayName = "";

    public FeedMechanism getMechanism() {
        return mechanism == null ? FeedMechanism.LEGACY : mechanism;
    }

    public String getMagazineFamily() {
        return magazineFamily == null ? "" : magazineFamily;
    }

    public int getMagazineCapacity() {
        return Math.max(0, magazineCapacity);
    }

    public Identifier getAmmoId() {
        return ammoId;
    }

    public String getDisplayName() {
        return displayName == null ? "" : displayName;
    }

    public boolean isValidDetachableDefinition() {
        return getMechanism().usesDetachableMagazine()
                && !getMagazineFamily().isBlank()
                && getMagazineCapacity() > 0
                && getAmmoId() != null;
    }
}
