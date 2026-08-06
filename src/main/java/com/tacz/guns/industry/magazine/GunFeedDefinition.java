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

    /**
     * Maximum loose rounds one complete scripted reload action may insert for
     * an internal feed. Tube/cylinder/double-barrel scripts may visually feed
     * several rounds before FINISHING, so their default is full remaining
     * capacity rather than one silently reserved round.
     */
    @SerializedName("reload_batch")
    private int reloadBatch = 0;

    /** Capacity of one bridge clip/speedloader. Internal capacity remains magazine_capacity. */
    @SerializedName("feed_device_capacity")
    private int feedDeviceCapacity = 0;

    /** Empty bridge clips/speedloaders may be retained for refilling when true. */
    @SerializedName("feed_device_reusable")
    private boolean feedDeviceReusable = true;

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

    public int getFeedDeviceCapacity() {
        return Math.max(0, feedDeviceCapacity);
    }

    public boolean isFeedDeviceReusable() {
        return feedDeviceReusable;
    }

    public int getReloadBatch() {
        if (reloadBatch > 0) {
            return Math.min(reloadBatch, Math.max(1, getMagazineCapacity()));
        }
        if (getMechanism().usesLoadingDevice()) {
            // One bridge clip/speedloader is the source for a reload cycle;
            // never silently pull rounds from a second device in that same
            // animation merely because the internal gun has more free space.
            return Math.max(1, Math.min(getMagazineCapacity(), getFeedDeviceCapacity()));
        }
        // A reload action owns one complete scripted reload cycle. Tube and
        // cylinder scripts commonly feed several visible rounds during that
        // cycle; defaulting them to one silently desynchronises actual loose
        // ammo extraction from the animation. Capacity is therefore the safe
        // data-driven default; packs may still declare a smaller explicit cap.
        return Math.max(1, getMagazineCapacity());
    }

    /** External carrier: detachable magazine or physical belt/ammo-box item. */
    public boolean isValidExternalCarrierDefinition() {
        return (getMechanism().usesDetachableMagazine() || getMechanism() == FeedMechanism.BELT)
                && !getMagazineFamily().isBlank()
                && getMagazineCapacity() > 0
                && getAmmoId() != null;
    }

    public boolean isValidDetachableDefinition() {
        return getMechanism().usesDetachableMagazine()
                && !getMagazineFamily().isBlank()
                && getMagazineCapacity() > 0
                && getAmmoId() != null;
    }

    /**
     * Tube, cylinder, internal box, single-shot and loading-device feeds store
     * their authoritative round count in gun NBT. A bridge clip/speedloader is
     * an inventory source for that count, not an InstalledMagazine replacement.
     */
    public boolean isValidInternalDefinition() {
        return switch (getMechanism()) {
            case INTERNAL_BOX, TUBE, REVOLVER, SINGLE_SHOT -> getMagazineCapacity() > 0 && getAmmoId() != null;
            case STRIPPER_CLIP, SPEEDLOADER -> isValidLoadingDeviceDefinition();
            default -> false;
        };
    }

    public boolean isValidLoadingDeviceDefinition() {
        return getMechanism().usesLoadingDevice()
                && !getMagazineFamily().isBlank()
                && getMagazineCapacity() > 0
                && getFeedDeviceCapacity() > 0
                && getAmmoId() != null;
    }
}
