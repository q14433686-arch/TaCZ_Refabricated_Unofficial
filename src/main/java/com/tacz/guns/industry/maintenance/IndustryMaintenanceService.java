package com.tacz.guns.industry.maintenance;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Server-authoritative industrial maintenance state.
 *
 * <p>Condition/Fouling and the deterministic feed-jam draw are written only
 * after a real round has been consumed. A feed jam is never a client-only
 * effect: it is an NBT fault set by this service, rejects later shots on the
 * server, and can be removed only after the separately validated manual-bolt
 * transaction reports that it chambered a round. Critical-condition lockout
 * remains a service-bench-only fault.</p>
 */
public final class IndustryMaintenanceService {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONDITION = 10_000;
    /** Datapack-extensible blocks that expose service components to dirt/sand/mud contamination. */
    public static final TagKey<Block> CONTAMINANT_BLOCKS = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tacz", "maintenance_contaminants")
    );

    public static final String SCHEMA_TAG = "IndustryMaintenanceSchema";
    public static final String RECEIVER_TAG = "IndustryConditionReceiver";
    public static final String BOLT_TAG = "IndustryConditionBolt";
    public static final String BARREL_TAG = "IndustryConditionBarrel";
    public static final String TRIGGER_TAG = "IndustryConditionTrigger";
    public static final String RECOIL_TAG = "IndustryConditionRecoil";
    public static final String FOULING_TAG = "IndustryFouling";
    public static final String SEED_TAG = "IndustryMaintenanceSeed";
    public static final String SHOTS_TAG = "IndustryMaintenanceShots";
    /** Server-authoritative C.1/C.2 fault state; never written from client prediction. */
    public static final String JAM_TAG = "IndustryJam";
    public static final String LOCKOUT_JAM = "lockout";
    public static final String FEED_JAM = "feed";

    /** Existing industrial assembly provenance written by Create results. */
    public static final String ASSEMBLY_PLATFORM_TAG = "IndustryAssemblyPlatform";
    public static final String ASSEMBLY_RECIPE_TAG = "IndustryAssemblyRecipe";
    public static final String ASSEMBLY_TIER_TAG = "IndustryAssemblyTier";
    public static final String ASSEMBLY_ACTION_TAG = "IndustryAssemblyActionProfile";
    public static final String ASSEMBLY_TOOLING_SCOPE_TAG = "IndustryAssemblyToolingScope";

    private IndustryMaintenanceService() {
    }

    public static boolean isFeatureEnabled() {
        return IndustryProfileManager.isCreateFlyProfileActive()
                && SyncConfig.INDUSTRY_MAINTENANCE_SCOPE != null;
    }

    /**
     * Safe migration point called only by server gameplay paths. Missing or old
     * data always starts full and clean; it is never inferred from an item's
     * age, damage value, gun level, or an arbitrary third-party NBT field.
     */
    public static boolean migrateIfEligible(ItemStack gun) {
        if (!isEligible(gun)) {
            return false;
        }
        ItemNbtUtils.updateTag(gun, tag -> migrateTag(tag));
        return true;
    }

    /**
     * Compatibility return for callers that only need to know whether real
     * maintenance accounting occurred. New firing paths use
     * {@link #recordSuccessfulShotOutcome(LivingEntity, ItemStack)} so they can
     * immediately sync a server-created feed fault to the firing client.
     */
    public static boolean recordSuccessfulShot(LivingEntity shooter, ItemStack gun) {
        return recordSuccessfulShotOutcome(shooter, gun).recorded();
    }

    /**
     * Called only after {@code reduceAmmoOnce()} succeeded. The current round
     * has already fired; a C.2 feed jam can therefore only stop a later trigger
     * pull and can never swallow or refund the just-fired round.
     */
    public static ShotOutcome recordSuccessfulShotOutcome(LivingEntity shooter, ItemStack gun) {
        if (!migrateIfEligible(gun)) {
            return ShotOutcome.NONE;
        }
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        if (profile == null) {
            return ShotOutcome.NONE;
        }
        IndustryMaintenanceProfile.WearPerShot wear = profile.getWearPerShot();
        Exposure exposure = Exposure.capture(shooter, profile.getOperation());
        ItemNbtUtils.updateTag(gun, tag -> {
            migrateTag(tag);
            tag.putInt(RECEIVER_TAG, subtractWear(tag.getIntOr(RECEIVER_TAG, MAX_CONDITION), exposure.wear(wear.getReceiver())));
            tag.putInt(BOLT_TAG, subtractWear(tag.getIntOr(BOLT_TAG, MAX_CONDITION), exposure.wear(wear.getBolt())));
            tag.putInt(BARREL_TAG, subtractWear(tag.getIntOr(BARREL_TAG, MAX_CONDITION), exposure.wear(wear.getBarrel())));
            tag.putInt(TRIGGER_TAG, subtractWear(tag.getIntOr(TRIGGER_TAG, MAX_CONDITION), exposure.wear(wear.getTrigger())));
            tag.putInt(RECOIL_TAG, subtractWear(tag.getIntOr(RECOIL_TAG, MAX_CONDITION), exposure.wear(wear.getRecoil())));
            tag.putInt(FOULING_TAG, clampCondition(tag.getIntOr(FOULING_TAG, 0)
                    + exposure.fouling(profile.getFoulingPerShot())));
            long oldShots = Math.max(0L, tag.getLongOr(SHOTS_TAG, 0L));
            tag.putLong(SHOTS_TAG, oldShots == Long.MAX_VALUE ? Long.MAX_VALUE : oldShots + 1L);
        });
        refreshFaultState(gun);

        // Lockout always wins. A profile without a verified manual clear action
        // remains phase-A/C.1 only and can never opt into random feed faults by
        // accident, including generic surveyed third-party profiles.
        if (isLockout(gun) || !canCreateFeedJam(shooter, gun, profile)) {
            return ShotOutcome.RECORDED;
        }
        Snapshot snapshot = getSnapshot(gun);
        if (!shouldCreateFeedJam(gun, snapshot, profile)) {
            return ShotOutcome.RECORDED;
        }
        ItemNbtUtils.updateTag(gun, tag -> tag.putString(JAM_TAG, FEED_JAM));
        return ShotOutcome.FEED_JAMMED;
    }

    /** C.1 deterministic hard-stop; only the service bench can remove it. */
    public static boolean isLockout(ItemStack gun) {
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        Snapshot snapshot = getSnapshot(gun);
        return profile != null && snapshot.eligible()
                && snapshot.minimumCondition() <= profile.getJam().getCriticalCondition();
    }

    /** True when the server has recorded a C.2 feed failure on this maintained gun. */
    public static boolean isFeedJammed(ItemStack gun) {
        return getSnapshot(gun).eligible()
                && FEED_JAM.equals(ItemNbtUtils.getTag(gun).getStringOr(JAM_TAG, ""))
                // Do not trap a world after a datapack removes an opt-in
                // clear contract. The next server maintenance migration also
                // strips that stale tag in refreshFaultState().
                && canClearFeedJamWithBolt(gun);
    }

    /** Both fault kinds block a later trigger pull before any ammunition mutation. */
    public static boolean isJammed(ItemStack gun) {
        return isLockout(gun) || isFeedJammed(gun);
    }

    /**
     * A data profile may request a bolt clear only when the loaded gun really
     * exposes TACZ's server-side {@link Bolt#MANUAL_ACTION} cycle. This runtime
     * guard prevents a third-party JSON declaration from inventing a clear
     * action for a closed/open-bolt gun that has no audited bolt transaction.
     */
    public static boolean canClearFeedJamWithBolt(ItemStack gun) {
        if (!isEligible(gun)) {
            return false;
        }
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        return profile != null
                && profile.getJam().getClearAction() == IndustryMaintenanceProfile.ClearAction.BOLT
                && hasManualBolt(gun);
    }

    /**
     * Server entry for the explicit C2S clear request. It does not remove the
     * fault itself: {@link com.tacz.guns.entity.shooter.LivingEntityBolt} must
     * first start and finish its normal bolt script, then prove that a round
     * reached the chamber.
     */
    public static boolean requestFeedJamClear(LivingEntity shooter) {
        if (shooter == null) {
            return false;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        ShooterDataHolder data = operator.getDataHolder();
        if (data.currentGunItem == null || data.isBolting) {
            return false;
        }
        ItemStack gun = data.currentGunItem.get();
        if (!isFeedJammed(gun) || !canClearFeedJamWithBolt(gun)) {
            return false;
        }
        data.industryFeedJamClearRequested = true;
        operator.bolt();
        boolean accepted = data.industryFeedJamClearInProgress;
        if (!accepted) {
            data.industryFeedJamClearRequested = false;
        }
        return accepted;
    }

    /** Remove only a feed fault after the verified manual bolt has chambered a round. */
    public static boolean completeFeedJamClear(ItemStack gun, boolean chamberedRound) {
        if (!chamberedRound || !isFeedJammed(gun) || !canClearFeedJamWithBolt(gun)) {
            return false;
        }
        ItemNbtUtils.updateTag(gun, tag -> {
            if (FEED_JAM.equals(tag.getStringOr(JAM_TAG, ""))) {
                tag.remove(JAM_TAG);
            }
        });
        return true;
    }

    private static void refreshFaultState(ItemStack gun) {
        boolean lockout = isLockout(gun);
        boolean feedClearable = canClearFeedJamWithBolt(gun);
        ItemNbtUtils.updateTag(gun, tag -> {
            String current = tag.getStringOr(JAM_TAG, "");
            if (lockout) {
                tag.putString(JAM_TAG, LOCKOUT_JAM);
            } else if (FEED_JAM.equals(current) && !feedClearable) {
                // A removed/changed datapack profile must not strand an old
                // feed tag on a gun with no possible real clear action.
                tag.remove(JAM_TAG);
            } else if (LOCKOUT_JAM.equals(current)) {
                tag.remove(JAM_TAG);
            }
        });
    }

    private static boolean canCreateFeedJam(LivingEntity shooter, ItemStack gun, IndustryMaintenanceProfile profile) {
        return profile.getJam().getClearAction() == IndustryMaintenanceProfile.ClearAction.BOLT
                && canClearFeedJamWithBolt(gun)
                && hasFeedableRound(shooter, gun);
    }

    private static boolean hasFeedableRound(LivingEntity shooter, ItemStack gun) {
        if (shooter == null || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        boolean needCheckAmmo = IGunOperator.fromLivingEntity(shooter).needCheckAmmo();
        return iGun.useInventoryAmmo(gun)
                ? iGun.hasInventoryAmmo(shooter, gun, needCheckAmmo)
                : iGun.getCurrentAmmoCount(gun) > 0;
    }

    private static boolean hasManualBolt(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        Identifier gunId = iGun.getGunId(gun);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getBolt() == Bolt.MANUAL_ACTION)
                .orElse(false);
    }

    /**
     * Stable, per-item/per-shot Bernoulli draw. It is intentionally not based
     * on client RNG, world time, entity UUID, or packet arrival order, so a
     * server reload/replay cannot turn a known shot into a different outcome.
     */
    private static boolean shouldCreateFeedJam(ItemStack gun, Snapshot snapshot, IndustryMaintenanceProfile profile) {
        IndustryMaintenanceProfile.JamThresholds jam = profile.getJam();
        int warning = jam.getWarningCondition();
        int critical = jam.getCriticalCondition();
        int span = Math.max(1, warning - critical);
        float conditionRisk = Math.clamp((warning - snapshot.minimumCondition()) / (float) span, 0.0F, 1.0F);
        if (conditionRisk <= 0.0F) {
            return false;
        }
        float foulingRisk = Math.clamp(snapshot.fouling() / (float) MAX_CONDITION, 0.0F, 1.0F);
        // Feed trouble rises gently at the warning threshold and sharply only
        // near critical condition. Fouling modulates the declared maximum but
        // cannot make a fresh, clean weapon randomly jam.
        double chance = jam.getMaxChance() * conditionRisk * conditionRisk * conditionRisk
                * (0.15D + 0.85D * foulingRisk);
        if (chance <= 0.0D) {
            return false;
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        long seed = tag.getLongOr(SEED_TAG, 0L);
        long shots = Math.max(0L, tag.getLongOr(SHOTS_TAG, 0L));
        long mixed = mix64(seed ^ (shots * 0x9E3779B97F4A7C15L));
        double draw = (mixed >>> 11) * 0x1.0p-53;
        return draw < Math.min(chance, 1.0D);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /**
     * Read-only client/server projection for Tooltip and HUD rendering. A gun
     * with industrial provenance but no saved schema is shown as full/clean so
     * an old save is never visually labelled damaged before server migration.
     */
    public static Snapshot getSnapshot(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun)) {
            return Snapshot.NONE;
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        boolean provenance = hasIndustrialOrigin(tag);
        boolean stored = tag.contains(SCHEMA_TAG);
        if (!provenance && !stored) {
            return Snapshot.NONE;
        }
        int receiver = clampCondition(tag.getIntOr(RECEIVER_TAG, MAX_CONDITION));
        int bolt = clampCondition(tag.getIntOr(BOLT_TAG, MAX_CONDITION));
        int barrel = clampCondition(tag.getIntOr(BARREL_TAG, MAX_CONDITION));
        int trigger = clampCondition(tag.getIntOr(TRIGGER_TAG, MAX_CONDITION));
        int recoil = clampCondition(tag.getIntOr(RECOIL_TAG, MAX_CONDITION));
        int fouling = clampCondition(tag.getIntOr(FOULING_TAG, 0));
        long shots = Math.max(0L, tag.getLongOr(SHOTS_TAG, 0L));
        return new Snapshot(true, receiver, bolt, barrel, trigger, recoil, fouling, shots);
    }

    @Nullable
    public static Component getTooltipLine(ItemStack gun) {
        Snapshot snapshot = getSnapshot(gun);
        if (!snapshot.eligible()) {
            return null;
        }
        String condition = percentage(snapshot.minimumCondition());
        String fouling = percentage(snapshot.fouling());
        boolean lockout = isLockout(gun);
        boolean feedJam = !lockout && isFeedJammed(gun);
        Component state = lockout
                ? Component.translatable("tooltip.tacz.maintenance.lockout")
                : feedJam ? Component.translatable("tooltip.tacz.maintenance.feed_jam")
                : Component.translatable(snapshot.status().translationKey());
        int color = lockout || feedJam ? 0xE05252 : snapshot.status().color();
        return Component.translatable("tooltip.tacz.maintenance.status", state, condition, fouling)
                .withStyle(style -> style.withColor(color));
    }

    @Nullable
    public static Component getDurabilityGradeLine(ItemStack gun) {
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        if (profile == null || !getSnapshot(gun).eligible()) {
            return null;
        }
        return Component.translatable("tooltip.tacz.maintenance.grade",
                Component.translatable("tooltip.tacz.maintenance.grade." + profile.getDurabilityGrade()),
                profile.getExpectedBarrelShots())
                .withStyle(style -> style.withColor(0x8AA7B7));
    }

    public static boolean hasIndustrialOrigin(ItemStack gun) {
        return hasIndustrialOrigin(ItemNbtUtils.getTag(gun));
    }

    private static boolean isEligible(ItemStack gun) {
        if (!isFeatureEnabled() || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        Identifier gunId = iGun.getGunId(gun);
        if (gunId == null || CommonAssetsManager.get().getGunIndex(gunId) == null) {
            return false;
        }
        IndustryMaintenanceScope scope = configuredScope();
        if (scope == IndustryMaintenanceScope.ALL_GUNS) {
            return getProfileFor(gun) != null;
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        return hasIndustrialOrigin(tag) && getProfileFor(gun) != null;
    }

    @Nullable
    private static IndustryMaintenanceProfile getProfileFor(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return null;
        }
        Identifier gunId = iGun.getGunId(gun);
        IndustryMaintenanceProfile explicit = CommonAssetsManager.get().getIndustryMaintenanceProfile(gunId);
        if (explicit != null && explicit.isValid()) {
            return explicit;
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        IndustryMaintenanceScope scope = configuredScope();
        if (scope == IndustryMaintenanceScope.ALL_GUNS || isSurveyedIndustrialOrigin(tag)) {
            return IndustryMaintenanceProfile.genericSurveyed();
        }
        return null;
    }

    private static IndustryMaintenanceScope configuredScope() {
        if (SyncConfig.INDUSTRY_MAINTENANCE_SCOPE == null) {
            return IndustryMaintenanceScope.INDUSTRIAL_ASSEMBLY;
        }
        IndustryMaintenanceScope scope = SyncConfig.INDUSTRY_MAINTENANCE_SCOPE.get();
        return scope == null ? IndustryMaintenanceScope.INDUSTRIAL_ASSEMBLY : scope;
    }

    private static void migrateTag(CompoundTag tag) {
        int schema = Math.max(0, tag.getIntOr(SCHEMA_TAG, 0));
        if (schema <= 0) {
            tag.putInt(SCHEMA_TAG, SCHEMA_VERSION);
            tag.putInt(RECEIVER_TAG, MAX_CONDITION);
            tag.putInt(BOLT_TAG, MAX_CONDITION);
            tag.putInt(BARREL_TAG, MAX_CONDITION);
            tag.putInt(TRIGGER_TAG, MAX_CONDITION);
            tag.putInt(RECOIL_TAG, MAX_CONDITION);
            tag.putInt(FOULING_TAG, 0);
            tag.putLong(SEED_TAG, UUID.randomUUID().getLeastSignificantBits());
            tag.putLong(SHOTS_TAG, 0L);
            return;
        }
        // A partially-written old schema is repaired conservatively: missing
        // components begin full, existing values are merely clamped.
        tag.putInt(RECEIVER_TAG, clampCondition(tag.getIntOr(RECEIVER_TAG, MAX_CONDITION)));
        tag.putInt(BOLT_TAG, clampCondition(tag.getIntOr(BOLT_TAG, MAX_CONDITION)));
        tag.putInt(BARREL_TAG, clampCondition(tag.getIntOr(BARREL_TAG, MAX_CONDITION)));
        tag.putInt(TRIGGER_TAG, clampCondition(tag.getIntOr(TRIGGER_TAG, MAX_CONDITION)));
        tag.putInt(RECOIL_TAG, clampCondition(tag.getIntOr(RECOIL_TAG, MAX_CONDITION)));
        tag.putInt(FOULING_TAG, clampCondition(tag.getIntOr(FOULING_TAG, 0)));
        if (!tag.contains(SEED_TAG)) {
            tag.putLong(SEED_TAG, UUID.randomUUID().getLeastSignificantBits());
        }
        tag.putLong(SHOTS_TAG, Math.max(0L, tag.getLongOr(SHOTS_TAG, 0L)));
    }

    private static boolean hasIndustrialOrigin(CompoundTag tag) {
        return !tag.getStringOr(ASSEMBLY_PLATFORM_TAG, "").isBlank();
    }

    private static boolean isSurveyedIndustrialOrigin(CompoundTag tag) {
        String platform = tag.getStringOr(ASSEMBLY_PLATFORM_TAG, "");
        return platform.startsWith("surveyed/")
                || "surveyed".equals(tag.getStringOr(ASSEMBLY_TIER_TAG, ""));
    }

    private static int subtractWear(int condition, int wear) {
        return clampCondition(clampCondition(condition) - Math.max(0, wear));
    }

    private static int clampCondition(int value) {
        return Math.clamp(value, 0, MAX_CONDITION);
    }

    private static String percentage(int amount) {
        // Phase-A wear is intentionally gradual. Two decimals make the first
        // real shot visible in Tooltip (for example 99.97% / 0.03%) without
        // exposing raw 0..10000 implementation integers to players.
        return String.format(Locale.ROOT, "%.2f%%", clampCondition(amount) * 100.0D / MAX_CONDITION);
    }

    /** Read the two environment signals that are server-verifiable without client weather guesses. */
    private record Exposure(float wearMultiplier, float foulingMultiplier) {
        private static Exposure capture(LivingEntity shooter, IndustryMaintenanceProfile.OperationProfile operation) {
            float wear = operation.getWearMultiplier();
            float fouling = operation.getFoulingMultiplier();
            if (shooter != null) {
                if (shooter.isInWater()) {
                    wear *= operation.getSubmergedWearMultiplier();
                    fouling *= operation.getSubmergedFoulingMultiplier();
                }
                if (shooter.level().getBlockState(shooter.blockPosition().below()).is(CONTAMINANT_BLOCKS)) {
                    wear *= operation.getContaminantWearMultiplier();
                    fouling *= operation.getContaminantFoulingMultiplier();
                }
            }
            return new Exposure(Math.clamp(wear, 0.0F, 16.0F), Math.clamp(fouling, 0.0F, 16.0F));
        }

        private int wear(int base) {
            return base <= 0 || wearMultiplier <= 0.0F ? 0 : Math.max(1, (int) Math.ceil(base * wearMultiplier));
        }

        private int fouling(int base) {
            return base <= 0 || foulingMultiplier <= 0.0F ? 0 : Math.max(1, (int) Math.ceil(base * foulingMultiplier));
        }
    }

    /** Result of one actual server-side round after maintenance accounting. */
    public record ShotOutcome(boolean recorded, boolean feedJammed) {
        public static final ShotOutcome NONE = new ShotOutcome(false, false);
        public static final ShotOutcome RECORDED = new ShotOutcome(true, false);
        public static final ShotOutcome FEED_JAMMED = new ShotOutcome(true, true);
    }

    public enum Status {
        GOOD("tooltip.tacz.maintenance.good", 0x55D66B),
        SERVICE("tooltip.tacz.maintenance.service", 0xF2C14E),
        REPAIR("tooltip.tacz.maintenance.repair", 0xE58B42),
        OUT_OF_SERVICE("tooltip.tacz.maintenance.out_of_service", 0xE05252);

        private final String translationKey;
        private final int color;

        Status(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String translationKey() {
            return translationKey;
        }

        public int color() {
            return color;
        }
    }

    public record Snapshot(boolean eligible, int receiver, int bolt, int barrel, int trigger, int recoil,
                           int fouling, long shots) {
        public static final Snapshot NONE = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0L);

        public int minimumCondition() {
            return Math.min(Math.min(receiver, bolt), Math.min(barrel, Math.min(trigger, recoil)));
        }

        public Status status() {
            int minimum = minimumCondition();
            if (minimum >= 7_500 && fouling < 2_500) {
                return Status.GOOD;
            }
            if (minimum >= 4_500 && fouling < 6_000) {
                return Status.SERVICE;
            }
            if (minimum >= 1_500) {
                return Status.REPAIR;
            }
            return Status.OUT_OF_SERVICE;
        }
    }
}
