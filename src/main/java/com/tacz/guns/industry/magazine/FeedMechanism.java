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
    SINGLE_SHOT;

    public boolean usesDetachableMagazine() {
        return this == DETACHABLE_MAGAZINE;
    }

    /**
     * Reference profiles store the serialized data value instead of a Java enum
     * name so datapacks remain readable across versions. Unknown future device
     * types must keep {@code runtime_mechanism = legacy} until gameplay support
     * exists; this helper validates only mechanisms currently executable here.
     */
    public static boolean isKnownSerializedName(String value) {
        return switch (value == null ? "" : value) {
            case "legacy", "detachable_magazine", "internal_box", "tube", "revolver", "belt", "single_shot" -> true;
            default -> false;
        };
    }
}
