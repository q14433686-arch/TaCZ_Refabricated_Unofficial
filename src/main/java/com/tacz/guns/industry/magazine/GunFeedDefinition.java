package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Additional, explicitly manufactured capacities for an external carrier.
     * The base {@link #magazineCapacity} is always the level-zero capacity;
     * each extra entry must match an actual extended-mag capacity exposed by
     * the currently loaded GunData. This is intentionally not a generic
     * "smaller magazines fit" guess.
     */
    @SerializedName("carrier_variants")
    private List<ExternalCarrierVariant> carrierVariants = List.of();

    /**
     * Maximum loose rounds one complete scripted reload action may insert for
     * an internal feed. Tube/cylinder/double-barrel scripts may visually feed
     * several rounds before FINISHING, so their default is full remaining
     * capacity rather than one silently reserved round.
     */
    @SerializedName("reload_batch")
    private int reloadBatch = 0;

    /**
     * Explicit animation contract for loading loose rounds.  A pack must opt
     * into {@link LooseReloadMode#SCRIPT_LOOP} before we drive a one-press,
     * one-round-at-a-time reload through its own repeated script feed points.
     */
    @SerializedName("loose_reload_mode")
    private LooseReloadMode looseReloadMode = LooseReloadMode.AUTO;

    /**
     * Maximum loose rounds inserted by one native complete reload action when
     * {@code loose_reload_mode = single_action}.  It is intentionally not used
     * for {@code script_loop}: that mode follows the pack's real per-round
     * timing and stops at full, empty source, or interruption.
     */
    @SerializedName("loose_reload_batch")
    private int looseReloadBatch = 0;

    /**
     * Optional ordered, audited branches for guns whose native reload script
     * can select a clip batch or a loose-round loop according to attachments,
     * missing rounds, and the availability of a physical device.
     */
    @SerializedName("reload_routes")
    private List<GunReloadRoute> reloadRoutes = List.of();

    /** Capacity of one bridge clip/speedloader. Internal capacity remains magazine_capacity. */
    @SerializedName("feed_device_capacity")
    private int feedDeviceCapacity = 0;

    /**
     * Retained only for backwards-compatible data parsing. Bridge clips and
     * speedloaders are physical reusable tools: an empty device is never
     * deleted by the reload transaction.
     */
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

    /**
     * All explicit external-carrier identities, with the base declaration
     * first. Internal feeds and loading devices deliberately return no carrier
     * stack variants because their {@code magazine_capacity} describes receiver
     * capacity rather than a removable ItemStack.
     */
    public List<ExternalCarrierVariant> getExternalCarrierVariants() {
        if (!isValidExternalCarrierDefinition()) {
            return List.of();
        }
        List<ExternalCarrierVariant> variants = new ArrayList<>();
        Set<Integer> seenCapacities = new HashSet<>();
        ExternalCarrierVariant base = new ExternalCarrierVariant(getMagazineCapacity(), getDisplayName());
        variants.add(base);
        seenCapacities.add(base.getCapacity());
        if (carrierVariants != null) {
            for (ExternalCarrierVariant variant : carrierVariants) {
                if (variant != null && variant.isValid() && seenCapacities.add(variant.getCapacity())) {
                    variants.add(variant);
                }
            }
        }
        return List.copyOf(variants);
    }

    /**
     * Returns the declared identity for this exact carrier capacity, or null
     * when a larger capacity was not explicitly audited for this receiver.
     */
    public ExternalCarrierVariant getExternalCarrierVariant(int capacity) {
        int safeCapacity = Math.max(0, capacity);
        for (ExternalCarrierVariant variant : getExternalCarrierVariants()) {
            if (variant.getCapacity() == safeCapacity) {
                return variant;
            }
        }
        return null;
    }

    /**
     * Selects the smallest explicit carrier that can preserve this many legacy
     * rounds during migration. Returning null is intentional: callers must
     * retain legacy state rather than silently clamp ammunition into the base
     * magazine when no real carrier capacity has been declared.
     */
    public ExternalCarrierVariant getExternalCarrierVariantForRounds(int rounds) {
        int required = Math.max(0, rounds);
        ExternalCarrierVariant selected = null;
        for (ExternalCarrierVariant variant : getExternalCarrierVariants()) {
            if (variant.getCapacity() >= required
                    && (selected == null || variant.getCapacity() < selected.getCapacity())) {
                selected = variant;
            }
        }
        return selected;
    }

    /**
     * Existing lower-capacity members of the same explicit family remain
     * compatible as before. A capacity above the receiver's base declaration,
     * however, must be one of the exact audited variants above.
     */
    public boolean acceptsExternalCarrierCapacity(int capacity) {
        int safeCapacity = Math.max(0, capacity);
        return safeCapacity > 0 && (safeCapacity <= getMagazineCapacity()
                || getExternalCarrierVariant(safeCapacity) != null);
    }

    public int getFeedDeviceCapacity() {
        return Math.max(0, feedDeviceCapacity);
    }

    public boolean isFeedDeviceReusable() {
        // A bridge clip/speedloader is not ammunition packaging. Its rounds
        // may reach zero, but the physical loading tool remains available for
        // unloading/refilling and must never disappear as a reload side effect.
        return getMechanism().usesPhysicalFeedDevice() || feedDeviceReusable;
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

    /**
     * Resolves the compatibility-safe default without guessing from legacy gun
     * data.  A bridge clip/speedloader has no honest loose-round fallback by
     * default because its pack may only contain a batch/clip animation.
     */
    public LooseReloadMode getLooseReloadMode() {
        LooseReloadMode configured = looseReloadMode == null ? LooseReloadMode.AUTO : looseReloadMode;
        if (configured != LooseReloadMode.AUTO) {
            return configured;
        }
        return getMechanism().usesPhysicalFeedDevice() ? LooseReloadMode.NONE : LooseReloadMode.SINGLE_ACTION;
    }

    public boolean allowsLooseReload() {
        return getLooseReloadMode() != LooseReloadMode.NONE;
    }

    public boolean usesScriptedLooseReloadLoop() {
        return getLooseReloadMode() == LooseReloadMode.SCRIPT_LOOP;
    }

    /**
     * Batch size for a non-looping loose-round action.  Script loops never use
     * this value: their source is transferred at each real script feed event.
     */
    public int getLooseReloadBatch() {
        if (!allowsLooseReload()) {
            return 0;
        }
        if (usesScriptedLooseReloadLoop()) {
            return Math.max(1, getMagazineCapacity());
        }
        if (looseReloadBatch > 0) {
            return Math.min(looseReloadBatch, Math.max(1, getMagazineCapacity()));
        }
        return getReloadBatch();
    }

    /**
     * Ordered explicit reload branches. Invalid entries are ignored so one bad
     * data-pack route cannot disable the legacy-safe fallback for every gun.
     */
    public List<GunReloadRoute> getReloadRoutes() {
        if (reloadRoutes == null || reloadRoutes.isEmpty()) {
            return List.of();
        }
        List<GunReloadRoute> valid = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (GunReloadRoute route : reloadRoutes) {
            if (route != null && route.isValid() && seenIds.add(route.getId())) {
                valid.add(route);
            }
        }
        return List.copyOf(valid);
    }

    public boolean hasReloadRoutes() {
        return !getReloadRoutes().isEmpty();
    }

    public GunReloadRoute getReloadRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return null;
        }
        return getReloadRoutes().stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst().orElse(null);
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
     * their authoritative round count in {@code InternalFeedAmmoCount}. A
     * bridge clip/speedloader is an inventory source for that count, not an
     * InstalledMagazine replacement.
     *
     * <p>An en-bloc clip is deliberately <strong>not</strong> an internal-feed
     * definition here. It has its own {@code InstalledEnBlocClip} transaction:
     * treating it as both kinds of feed makes {@link InternalFeedService}
     * start a second, incompatible reload plan after {@link EnBlocClipService}
     * has reserved the clip. In particular, an M1 then rejects the reload even
     * in Creative because its en-bloc profile intentionally has no loose-round
     * internal-feed route.</p>
     */
    public boolean isValidInternalDefinition() {
        return switch (getMechanism()) {
            case INTERNAL_BOX, TUBE, REVOLVER, SINGLE_SHOT -> getMagazineCapacity() > 0 && getAmmoId() != null;
            case STRIPPER_CLIP, SPEEDLOADER -> isValidLoadingDeviceDefinition();
            case EN_BLOC_CLIP, LEGACY, DETACHABLE_MAGAZINE, BELT -> false;
        };
    }

    /** Bridge clips and speedloaders are inventory sources for an internal feed. */
    public boolean isValidLoadingDeviceDefinition() {
        return getMechanism().usesLoadingDevice()
                && hasValidPhysicalFeedDeviceFields();
    }

    /** An en-bloc clip is physically installed into the receiver until empty. */
    public boolean isValidEnBlocClipDefinition() {
        return getMechanism().usesEnBlocClip()
                && hasValidPhysicalFeedDeviceFields();
    }

    /** Shared configured ItemStack requirements for all physical clips/loaders. */
    public boolean hasValidPhysicalFeedDeviceFields() {
        return !getMagazineFamily().isBlank()
                && getMagazineCapacity() > 0
                && getFeedDeviceCapacity() > 0
                && getAmmoId() != null;
    }

    /**
     * Validate an add-on declaration against the actual loaded TACZ GunData.
     * This is intentionally stricter than checking JSON shape: a pack cannot
     * accidentally wire a physical STANAG magazine to a same-named gun whose
     * currently loaded ammo or receiver capacity has changed.
     */
    public Validation validateAgainst(Identifier gunId, CommonGunIndex index) {
        if (gunId == null || index == null || index.getGunData() == null) {
            return Validation.invalid("target GunIndex/GunData is absent");
        }
        FeedMechanism declared = getMechanism();
        if (declared == FeedMechanism.LEGACY) {
            return Validation.invalid("mechanism=legacy does not opt into a physical-feed transaction");
        }
        GunData data = index.getGunData();
        if (getAmmoId() == null || !getAmmoId().equals(data.getAmmoId())) {
            return Validation.invalid("declared ammo does not equal loaded GunData.ammo");
        }
        int capacity = getMagazineCapacity();
        if (capacity != data.getAmmoAmount()) {
            return Validation.invalid("declared magazine_capacity does not equal loaded GunData.ammo_amount");
        }
        if (getDisplayName().isBlank()) {
            return Validation.invalid("display_name is required for a player-visible feed device");
        }
        boolean validMechanism = switch (declared) {
            case DETACHABLE_MAGAZINE, BELT -> isValidExternalCarrierDefinition();
            case INTERNAL_BOX, TUBE, REVOLVER, SINGLE_SHOT -> isValidInternalDefinition();
            case STRIPPER_CLIP, SPEEDLOADER -> isValidLoadingDeviceDefinition();
            case EN_BLOC_CLIP -> isValidEnBlocClipDefinition()
                    && getFeedDeviceCapacity() == capacity;
            case LEGACY -> false;
        };
        if (!validMechanism) {
            return Validation.invalid("required mechanism/family/capacity/feed-device fields are incomplete");
        }
        Validation carrierVariantsValidation = validateExternalCarrierVariants(declared, data);
        if (!carrierVariantsValidation.valid()) {
            return carrierVariantsValidation;
        }
        if (reloadRoutes != null && !reloadRoutes.isEmpty() && getReloadRoutes().isEmpty()) {
            return Validation.invalid("all declared reload_routes are invalid");
        }
        return Validation.success();
    }

    /**
     * A carrier variant is not an arbitrary capacity override. TACZ's extended
     * magazine attachment can expose at most its first three levels at runtime,
     * so every additional physical carrier must exactly match one of those
     * loaded values. Some old gun packs carry a fourth archival number; it is
     * deliberately not accepted until the runtime can actually select it.
     */
    private Validation validateExternalCarrierVariants(FeedMechanism declared, GunData data) {
        if (carrierVariants == null || carrierVariants.isEmpty()) {
            return Validation.success();
        }
        if (declared != FeedMechanism.DETACHABLE_MAGAZINE && declared != FeedMechanism.BELT) {
            return Validation.invalid("carrier_variants are only valid for detachable_magazine or belt");
        }
        Set<Integer> seenCapacities = new HashSet<>();
        seenCapacities.add(getMagazineCapacity());
        for (ExternalCarrierVariant variant : carrierVariants) {
            if (variant == null || !variant.isValid()) {
                return Validation.invalid("carrier_variants need positive capacity and display_name");
            }
            int capacity = variant.getCapacity();
            if (!seenCapacities.add(capacity)) {
                return Validation.invalid("carrier_variants cannot duplicate base or another capacity");
            }
            if (!matchesRuntimeExtendedCapacity(capacity, data)) {
                return Validation.invalid("carrier_variants capacity does not equal a selectable loaded GunData.extended_mag_ammo_amount value");
            }
        }
        return Validation.success();
    }

    private static boolean matchesRuntimeExtendedCapacity(int capacity, GunData data) {
        if (data == null || data.getExtendedMagAmmoAmount() == null) {
            return false;
        }
        int[] values = data.getExtendedMagAmmoAmount();
        for (int level = 0; level < Math.min(3, values.length); level++) {
            if (values[level] == capacity) {
                return true;
            }
        }
        return false;
    }

    public record Validation(boolean valid, String reason) {
        public static Validation success() { return new Validation(true, ""); }
        public static Validation invalid(String reason) { return new Validation(false, reason == null ? "invalid declaration" : reason); }
    }
}
