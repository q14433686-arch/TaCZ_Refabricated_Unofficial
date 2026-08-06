package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.attachment.AttachmentType;

import java.util.Locale;

/**
 * One audited branch of a gun pack's native reload script and state machine.
 *
 * <p>Most guns have one obvious route. Some historical internal magazines,
 * however, genuinely have two: a full bridge-clip animation under one set of
 * conditions and a repeated loose-round animation under another. This data
 * records those conditions explicitly instead of guessing from
 * {@code reload.type} or from an animation filename.</p>
 */
public final class GunReloadRoute {
    @SerializedName("id")
    private String id = "";

    @SerializedName("source")
    private ReloadRouteSource source = ReloadRouteSource.LOOSE_AMMO;

    /**
     * True when the gun pack's Lua script owns each feed point. Central
     * FEEDING -> FINISHING fallback is disabled so interruption preserves only
     * rounds the native animation actually reached.
     */
    @SerializedName("script_driven")
    private boolean scriptDriven;

    @SerializedName("min_missing_rounds")
    private int minimumMissingRounds = 1;

    /** Zero means no upper bound. */
    @SerializedName("max_missing_rounds")
    private int maximumMissingRounds;

    /** Null means either tactical state is valid; otherwise the route requires this exact state. */
    @SerializedName("require_tactical")
    private Boolean requireTactical;

    /**
     * A device route may require a full clip even when a partly-filled clip is
     * technically compatible. This prevents a five-round clip animation from
     * silently consuming only three unanimated rounds.
     */
    @SerializedName("min_source_rounds")
    private int minimumSourceRounds = 1;

    /** Zero means use the gun definition's normal reload_batch. */
    @SerializedName("max_transfer_rounds")
    private int maximumTransferRounds;

    /** Extra real source rounds consumed outside the magazine-fill target, e.g. an empty-chamber load. */
    @SerializedName("extra_source_rounds")
    private int extraSourceRounds;

    /** Optional attachment slot that must be empty for this route. */
    @SerializedName("require_attachment_empty")
    private String requiredAttachmentEmpty = "";

    /** Optional attachment slot that must be occupied for this route. */
    @SerializedName("require_attachment_present")
    private String requiredAttachmentPresent = "";

    /**
     * Compatibility selector for a pack whose existing Lua chooses its
     * individual-round animation by testing whether an attachment is present.
     * It affects only the reload script/state-machine view while this route is
     * active; it never adds a real attachment or changes gun statistics.
     */
    @SerializedName("animation_force_attachment_present")
    private String animationForceAttachmentPresent = "";

    public String getId() {
        return id == null ? "" : id;
    }

    public ReloadRouteSource getSource() {
        return source == null ? ReloadRouteSource.LOOSE_AMMO : source;
    }

    public boolean isScriptDriven() {
        return scriptDriven;
    }

    public int getMinimumMissingRounds() {
        return Math.max(1, minimumMissingRounds);
    }

    public int getMaximumMissingRounds() {
        return Math.max(0, maximumMissingRounds);
    }

    public boolean matchesTactical(boolean tactical) {
        return requireTactical == null || requireTactical.booleanValue() == tactical;
    }

    public int getMinimumSourceRounds() {
        return Math.max(1, minimumSourceRounds);
    }

    public int getMaximumTransferRounds(int fallback) {
        if (maximumTransferRounds > 0) {
            return maximumTransferRounds;
        }
        return Math.max(1, fallback);
    }

    public int getExtraSourceRounds() {
        return Math.max(0, extraSourceRounds);
    }

    public String getRequiredAttachmentEmpty() {
        return requiredAttachmentEmpty == null ? "" : requiredAttachmentEmpty;
    }

    public String getRequiredAttachmentPresent() {
        return requiredAttachmentPresent == null ? "" : requiredAttachmentPresent;
    }

    public String getAnimationForceAttachmentPresent() {
        return animationForceAttachmentPresent == null ? "" : animationForceAttachmentPresent;
    }

    public boolean matchesMissingRounds(int missing) {
        int safeMissing = Math.max(0, missing);
        return safeMissing >= getMinimumMissingRounds()
                && (getMaximumMissingRounds() == 0 || safeMissing <= getMaximumMissingRounds());
    }

    public boolean isValid() {
        return source != null && !getId().isBlank()
                && getMinimumMissingRounds() > 0
                && getMinimumSourceRounds() > 0
                && (getMaximumMissingRounds() == 0 || getMaximumMissingRounds() >= getMinimumMissingRounds())
                && validAttachmentSlot(getRequiredAttachmentEmpty())
                && validAttachmentSlot(getRequiredAttachmentPresent())
                && validAttachmentSlot(getAnimationForceAttachmentPresent())
                && (getRequiredAttachmentEmpty().isBlank() || getRequiredAttachmentPresent().isBlank()
                || !getRequiredAttachmentEmpty().equalsIgnoreCase(getRequiredAttachmentPresent()));
    }

    private static boolean validAttachmentSlot(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            AttachmentType.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
