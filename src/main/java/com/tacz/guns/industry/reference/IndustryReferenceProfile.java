package com.tacz.guns.industry.reference;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.industry.magazine.FeedMechanism;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Curated factual profile for one loaded gun identity.
 *
 * <p>The resource id is the gun id: for example
 * {@code data/rainforest/industry/reference/guns/fal.json} describes
 * {@code rainforest:fal}. This deliberately separates three concerns that
 * legacy gun-pack JSON conflates: the rendered/model identity, the real feed
 * device, and the industrial manufacturing projection. A historical pack's
 * {@code reload.type = magazine} is only an animation/API category and is
 * never treated as proof that it accepts a detachable magazine.</p>
 *
 * <p>Profiles are data only. They do not directly enable a new feed mechanism
 * until a later industrial projection explicitly supports it. This makes it
 * safe to record stripper clips, en-bloc clips, speedloaders, fuel canisters
 * and utility ammunition now rather than lying that they are magazines.</p>
 */
public final class IndustryReferenceProfile {
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9_./-]*");
    private static final Set<String> FEED_DEVICES = Set.of(
            "unknown", "detachable_magazine", "belt", "internal_box", "tube", "revolver", "single_shot",
            "stripper_clip", "en_bloc_clip", "speedloader", "fuel_canister", "utility"
    );
    private static final Set<String> CARRIER_BEHAVIOURS = Set.of(
            "unknown", "none", "inserted_retained", "inserted_ejected_empty", "consumed_loading_tool",
            "internal", "consumed", "utility"
    );
    private static final Set<String> AMMUNITION_CLASSES = Set.of(
            "unknown", "cartridge", "shot_shell", "grenade", "rocket", "fuel", "medical", "utility"
    );
    private static final Set<String> CONFIDENCE = Set.of(
            "curated", "pack_declared", "world_confirmed", "automatic_candidate"
    );
    private static final Set<String> TIERS = Set.of("legacy", "service", "advanced", "precision", "surveyed");

    @SerializedName("schema_version")
    private int schemaVersion = SCHEMA_VERSION;

    /** Stable factual/reference key; it is not required to equal a gun-pack id. */
    @SerializedName("canonical_model")
    private String canonicalModel = "";

    /** Optional human-facing label key or plain audited label. */
    @SerializedName("display_name")
    private String displayName = "";

    /** Action/system family such as gas_rifle, bolt_action, long_recoil or launcher. */
    @SerializedName("action")
    private String action = "unknown";

    @SerializedName("feed")
    private Feed feed = new Feed();

    @SerializedName("ammunition")
    private Ammunition ammunition = new Ammunition();

    @SerializedName("manufacturing")
    private Manufacturing manufacturing = new Manufacturing();

    /** curated, pack_declared, world_confirmed, or automatic_candidate. */
    @SerializedName("confidence")
    private String confidence = "automatic_candidate";

    /** Human-readable provenance; never fetched from the network at game time. */
    @SerializedName("evidence")
    private List<String> evidence = List.of();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getCanonicalModel() {
        return safe(canonicalModel, "");
    }

    public String getDisplayName() {
        return safe(displayName, "");
    }

    public String getAction() {
        return safe(action, "unknown");
    }

    public Feed getFeed() {
        return feed == null ? new Feed() : feed;
    }

    public Ammunition getAmmunition() {
        return ammunition == null ? new Ammunition() : ammunition;
    }

    public Manufacturing getManufacturing() {
        return manufacturing == null ? new Manufacturing() : manufacturing;
    }

    public String getConfidence() {
        return safe(confidence, "automatic_candidate");
    }

    public List<String> getEvidence() {
        return evidence == null ? List.of() : List.copyOf(evidence);
    }

    /**
     * Create a safe runtime candidate from facts TACZ can actually observe.
     *
     * <p>It intentionally records neither a detachable magazine nor a precise
     * mechanical action: index type and historical {@code reload.type} do not
     * carry that information reliably. An explicit profile can replace this
     * candidate once a pack author or compatibility layer supplies evidence.</p>
     */
    public static IndustryReferenceProfile automaticCandidate(Identifier gunId, CommonGunIndex gunIndex) {
        IndustryReferenceProfile profile = new IndustryReferenceProfile();
        GunData data = gunIndex == null ? null : gunIndex.getGunData();
        profile.canonicalModel = "surveyed/" + gunId.getNamespace() + "/" + gunId.getPath();
        profile.displayName = gunId.toString();
        profile.action = "surveyed_" + tokenOrFallback(gunIndex == null ? "unknown" : gunIndex.getType(), "unknown");
        profile.feed = new Feed();
        profile.feed.device = "unknown";
        profile.feed.runtimeMechanism = "legacy";
        profile.feed.carrierBehaviour = "unknown";
        profile.ammunition = new Ammunition();
        FeedType reload = data == null || data.getReloadData() == null ? null : data.getReloadData().getType();
        profile.ammunition.kind = reload == FeedType.FUEL ? "fuel"
                : reload == FeedType.INVENTORY ? "utility" : "unknown";
        Identifier ammo = data == null ? null : data.getAmmoId();
        profile.ammunition.nominal = ammo == null ? "unknown" : tokenOrFallback(ammo.getPath(), "unknown");
        profile.ammunition.expectedAmmo = ammo;
        profile.manufacturing = new Manufacturing();
        profile.manufacturing.profile = "surveyed";
        profile.manufacturing.tier = "surveyed";
        profile.confidence = "automatic_candidate";
        profile.evidence = List.of("loaded_gun_index_and_gun_data");
        return profile;
    }

    /**
     * Validate both the schema itself and the non-negotiable facts observable
     * from the loaded gun pack. A profile mismatch is disabled rather than
     * silently applied to a later/reused gun id.
     */
    public Validation validateAgainst(Identifier gunId, @Nullable CommonGunIndex gunIndex) {
        if (schemaVersion != SCHEMA_VERSION) {
            return Validation.invalid("unsupported schema_version " + schemaVersion);
        }
        if (!token(getCanonicalModel()) || !token(getAction())) {
            return Validation.invalid("canonical_model and action must be lower-case reference tokens");
        }
        if (!CONFIDENCE.contains(getConfidence())) {
            return Validation.invalid("unknown confidence '" + getConfidence() + "'");
        }
        if (!"automatic_candidate".equals(getConfidence()) && getEvidence().isEmpty()) {
            return Validation.invalid("curated/declared profiles require non-empty evidence");
        }

        Feed feed = getFeed();
        if (!FEED_DEVICES.contains(feed.getDevice()) || !CARRIER_BEHAVIOURS.contains(feed.getCarrierBehaviour())) {
            return Validation.invalid("unknown feed device or carrier behaviour");
        }
        if (!FeedMechanism.isKnownSerializedName(feed.getRuntimeMechanism())) {
            return Validation.invalid("unknown runtime_mechanism '" + feed.getRuntimeMechanism() + "'");
        }
        if (feed.getCapacity() < 0 || feed.getReloadBatch() < 0) {
            return Validation.invalid("feed capacity/reload_batch cannot be negative");
        }
        if (feed.usesExternalCarrier()) {
            if (feed.getFamily().isBlank() || feed.getCapacity() < 1) {
                return Validation.invalid("external carrier needs non-empty family and positive capacity");
            }
            String expectedRuntime = "belt".equals(feed.getDevice()) ? "belt" : "detachable_magazine";
            if (!expectedRuntime.equals(feed.getRuntimeMechanism())) {
                return Validation.invalid("external carrier runtime_mechanism must be " + expectedRuntime);
            }
        }

        Ammunition ammunition = getAmmunition();
        if (!AMMUNITION_CLASSES.contains(ammunition.getKind()) || !token(ammunition.getNominal())) {
            return Validation.invalid("unknown ammunition class or invalid nominal reference");
        }
        Manufacturing manufacturing = getManufacturing();
        if (!token(manufacturing.getProfile()) || !TIERS.contains(manufacturing.getTier())) {
            return Validation.invalid("invalid manufacturing profile/tier");
        }

        if (gunIndex == null || gunIndex.getGunData() == null) {
            return Validation.invalid("loaded gun index is absent for " + gunId);
        }
        GunData gunData = gunIndex.getGunData();
        if (ammunition.getExpectedAmmo() != null && !ammunition.getExpectedAmmo().equals(gunData.getAmmoId())) {
            return Validation.invalid("expected_ammo " + ammunition.getExpectedAmmo()
                    + " disagrees with loaded GunData ammo " + gunData.getAmmoId());
        }
        if (feed.getCapacity() > 0 && feed.getCapacity() != gunData.getAmmoAmount()) {
            return Validation.invalid("feed capacity " + feed.getCapacity()
                    + " disagrees with loaded GunData ammo_amount " + gunData.getAmmoAmount());
        }
        return Validation.success();
    }

    private static boolean token(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }

    private static String tokenOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return token(normalized) ? normalized : fallback;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Validation(boolean valid, String reason) {
        public static Validation success() {
            return new Validation(true, "");
        }

        public static Validation invalid(String reason) {
            return new Validation(false, reason == null ? "invalid profile" : reason);
        }
    }

    public static final class Feed {
        /** Physical/reference device, including devices not yet supported by runtime reload code. */
        @SerializedName("device")
        private String device = "unknown";

        /** Existing runtime mechanism; legacy keeps current TACZ behaviour intact. */
        @SerializedName("runtime_mechanism")
        private String runtimeMechanism = "legacy";

        @SerializedName("carrier_behavior")
        private String carrierBehaviour = "unknown";

        @SerializedName("family")
        private String family = "";

        @SerializedName("capacity")
        private int capacity;

        @SerializedName("reload_batch")
        private int reloadBatch;

        public String getDevice() {
            return safe(device, "unknown");
        }

        public String getRuntimeMechanism() {
            return safe(runtimeMechanism, "legacy");
        }

        public String getCarrierBehaviour() {
            return safe(carrierBehaviour, "unknown");
        }

        public String getFamily() {
            return safe(family, "");
        }

        public int getCapacity() {
            return capacity;
        }

        public int getReloadBatch() {
            return reloadBatch;
        }

        public boolean usesExternalCarrier() {
            return "detachable_magazine".equals(getDevice()) || "belt".equals(getDevice());
        }
    }

    public static final class Ammunition {
        @SerializedName("class")
        private String kind = "unknown";

        /** Stable reference token such as 762x39, 12g, or rpg_rocket. */
        @SerializedName("nominal")
        private String nominal = "unknown";

        /** Exact loaded AmmoId expected by this factual profile, when known. */
        @SerializedName("expected_ammo")
        private Identifier expectedAmmo;

        public String getKind() {
            return safe(kind, "unknown");
        }

        public String getNominal() {
            return safe(nominal, "unknown");
        }

        @Nullable
        public Identifier getExpectedAmmo() {
            return expectedAmmo;
        }
    }

    public static final class Manufacturing {
        @SerializedName("profile")
        private String profile = "surveyed";

        @SerializedName("tier")
        private String tier = "surveyed";

        public String getProfile() {
            return safe(profile, "surveyed");
        }

        public String getTier() {
            return safe(tier, "surveyed");
        }
    }
}
