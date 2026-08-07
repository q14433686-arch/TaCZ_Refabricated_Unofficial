package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;

/**
 * The real-world-ish feed mechanism declared by a gun-pack integration file.
 *
 * <p>The legacy TACZ {@code reload.type = magazine} field is intentionally not
 * reused here.  That historical value is also used by tube-fed shotguns and
 * revolvers, so treating it as a detachable magazine would break those guns.</p>
 */
public enum FeedMechanism {
    /** No industry integration was declared; preserve legacy TACZ ammunition behaviour. */
    @SerializedName("legacy")
    LEGACY,

    /** A removable physical magazine can be inserted/ejected. */
    @SerializedName("detachable_magazine")
    DETACHABLE_MAGAZINE,

    /** Internal box magazine, loaded in place. */
    @SerializedName("internal_box")
    INTERNAL_BOX,

    /** Tube-fed weapon. */
    @SerializedName("tube")
    TUBE,

    /** Cylinder-fed revolver. */
    @SerializedName("revolver")
    REVOLVER,

    /** Belt / ammunition-box-fed weapon. */
    @SerializedName("belt")
    BELT,

    /** Single-shot breech/loading tube. */
    @SerializedName("single_shot")
    SINGLE_SHOT,

    /** A stripper/bridge clip transfers rounds into an internal fixed magazine. */
    @SerializedName("stripper_clip")
    STRIPPER_CLIP,

    /** A speedloader transfers rounds into a cylinder; it is never installed in the gun. */
    @SerializedName("speedloader")
    SPEEDLOADER,

    /**
     * A physical clip that stays installed in an internal feed and is
     * automatically ejected once its final round has been fired.
     */
    @SerializedName("en_bloc_clip")
    EN_BLOC_CLIP;

    public boolean usesDetachableMagazine() {
        return this == DETACHABLE_MAGAZINE;
    }

    /** Bridge clips and speedloaders are physical loaders for an internal feed, not replacement magazines. */
    public boolean usesLoadingDevice() {
        return this == STRIPPER_CLIP || this == SPEEDLOADER;
    }

    /** An en-bloc clip is physically installed and therefore has its own transaction service. */
    public boolean usesEnBlocClip() {
        return this == EN_BLOC_CLIP;
    }

    /** Any reusable physical feed device represented by a configured magazine ItemStack. */
    public boolean usesPhysicalFeedDevice() {
        return usesLoadingDevice() || usesEnBlocClip();
    }

    public String serializedName() {
        return switch (this) {
            case LEGACY -> "legacy";
            case DETACHABLE_MAGAZINE -> "detachable_magazine";
            case INTERNAL_BOX -> "internal_box";
            case TUBE -> "tube";
            case REVOLVER -> "revolver";
            case BELT -> "belt";
            case SINGLE_SHOT -> "single_shot";
            case STRIPPER_CLIP -> "stripper_clip";
            case SPEEDLOADER -> "speedloader";
            case EN_BLOC_CLIP -> "en_bloc_clip";
        };
    }

    /** Reference profiles store the readable serialized mechanism name. */
    public static boolean isKnownSerializedName(String value) {
        return switch (value == null ? "" : value) {
            case "legacy", "detachable_magazine", "internal_box", "tube", "revolver", "belt", "single_shot",
                    "stripper_clip", "speedloader", "en_bloc_clip" -> true;
            default -> false;
        };
    }
}
