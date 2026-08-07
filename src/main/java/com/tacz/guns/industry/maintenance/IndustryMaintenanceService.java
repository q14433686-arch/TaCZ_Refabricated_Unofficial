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
 * after a real round has been consumed. C.3 derives heat, rain, wet-contact,
 * and contamination multipliers only from server-visible native state. A feed
 * jam is never a client-only effect: it is an NBT fault set by this service,
 * rejects later shots on the server, and can be removed only after the
 * separately validated manual-bolt transaction reports that it chambered a
 * round. Critical-condition lockout remains a service-bench-only fault.</p>
 */
public final class IndustryMaintenanceService {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONDITION = 10_000;
    /** Datapack-extensible blocks that expose service components to dirt/sand/mud contamination. */
    public static final TagKey<Block> CONTAMINANT_BLOCKS = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tacz", "maintenance_contaminants")
    );
    /** Wet ground/contact sources beyond direct immersion; evaluated server-side at the shooter's position. */
    public static final TagKey<Block> WET_EXPOSURE_BLOCKS = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tacz", "maintenance_wet_exposure")
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
    /** Deterministic C.1 threshold lockout. */
    public static final String LOCKOUT_JAM = "lockout";
    /** C.2 only: a real manual bolt can clear this after it chambers a round. */
    public static final String FEED_JAM = "feed";
    /** C.4 universal safe fault: requires the real industrial service bench, never a fabricated rack animation. */
    public static final String SERVICE_LOCKOUT_JAM = "service_lockout";

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
        ensureDeclaredHeatData(gun);
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
        Exposure exposure = Exposure.capture(shooter, gun, profile);
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

        // A deterministic C.1 threshold lockout always wins. C.4 then selects
        // one explicitly declared post-shot fault mode: only real manual-bolt
        // guns may create a feed jam; every other serviceable action family can
        // use the safe bench-only service lockout rather than a fake animation.
        if (isLockout(gun)) {
            return ShotOutcome.RECORDED;
        }
        Snapshot snapshot = getSnapshot(gun);
        IndustryMaintenanceProfile.FaultMode faultMode = profile.getJam().getFaultMode();
        boolean unsupportedFeed = faultMode == IndustryMaintenanceProfile.FaultMode.FEED
                && !canCreateFeedJam(shooter, gun, profile);
        boolean unsupportedServiceLockout = faultMode == IndustryMaintenanceProfile.FaultMode.SERVICE_LOCKOUT
                && !hasServiceBenchResolution(gun);
        if (faultMode == IndustryMaintenanceProfile.FaultMode.NONE || unsupportedFeed || unsupportedServiceLockout
                || !shouldCreateRandomFault(gun, snapshot, profile, exposure)) {
            return ShotOutcome.RECORDED;
        }
        return switch (faultMode) {
            case FEED -> {
                ItemNbtUtils.updateTag(gun, tag -> tag.putString(JAM_TAG, FEED_JAM));
                yield ShotOutcome.FEED_JAMMED;
            }
            case SERVICE_LOCKOUT -> {
                ItemNbtUtils.updateTag(gun, tag -> tag.putString(JAM_TAG, SERVICE_LOCKOUT_JAM));
                yield ShotOutcome.SERVICE_LOCKED;
            }
            case NONE -> ShotOutcome.RECORDED;
        };
    }

    /**
     * C.1 deterministic threshold lockout plus C.4's bench-only service fault.
     * Both are removed by a real industrial reassembly, never a client click or
     * an unaudited rack animation.
     */
    public static boolean isLockout(ItemStack gun) {
        // C.1 is intentionally recomputed from real component condition, so a
        // repaired/migrated old stack cannot remain stuck on a stale old tag.
        return isServiceLockout(gun) || isCriticalConditionLockout(gun);
    }

    private static boolean isCriticalConditionLockout(ItemStack gun) {
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        Snapshot snapshot = getSnapshot(gun);
        return profile != null && snapshot.eligible() && hasServiceBenchResolution(gun)
                && snapshot.minimumCondition() <= profile.getJam().getCriticalCondition();
    }

    /** True for the supported C.4 stochastic fault variant, useful for a distinct Tooltip line. */
    public static boolean isServiceLockout(ItemStack gun) {
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        return SERVICE_LOCKOUT_JAM.equals(ItemNbtUtils.getTag(gun).getStringOr(JAM_TAG, ""))
                && profile != null
                && profile.getJam().getFaultMode() == IndustryMaintenanceProfile.FaultMode.SERVICE_LOCKOUT
                && hasServiceBenchResolution(gun);
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
                && profile.getJam().getFaultMode() == IndustryMaintenanceProfile.FaultMode.FEED
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
        boolean criticalLockout = isCriticalConditionLockout(gun);
        boolean feedClearable = canClearFeedJamWithBolt(gun);
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        boolean serviceLockoutSupported = profile != null
                && profile.getJam().getFaultMode() == IndustryMaintenanceProfile.FaultMode.SERVICE_LOCKOUT
                && hasServiceBenchResolution(gun);
        ItemNbtUtils.updateTag(gun, tag -> {
            String current = tag.getStringOr(JAM_TAG, "");
            if (criticalLockout) {
                tag.putString(JAM_TAG, LOCKOUT_JAM);
            } else if (FEED_JAM.equals(current) && !feedClearable) {
                // A removed/changed datapack profile must not strand an old
                // feed tag on a gun with no possible real clear action.
                tag.remove(JAM_TAG);
            } else if (SERVICE_LOCKOUT_JAM.equals(current) && !serviceLockoutSupported) {
                // C.4 never leaves an item locked merely because the profile or
                // real industrial service provenance was removed later.
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

    /** Never create a universal C.4 lockout unless the actual bench can later resolve this exact gun. */
    private static boolean hasServiceBenchResolution(ItemStack gun) {
        return hasIndustrialOrigin(gun);
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
    private static boolean shouldCreateRandomFault(ItemStack gun, Snapshot snapshot,
                                                   IndustryMaintenanceProfile profile, Exposure exposure) {
        IndustryMaintenanceProfile.JamThresholds jam = profile.getJam();
        int warning = jam.getWarningCondition();
        int critical = jam.getCriticalCondition();
        int span = Math.max(1, warning - critical);
        float conditionRisk = Math.clamp((warning - snapshot.minimumCondition()) / (float) span, 0.0F, 1.0F);
        if (conditionRisk <= 0.0F) {
            return false;
        }
        float foulingRisk = Math.clamp(snapshot.fouling() / (float) MAX_CONDITION, 0.0F, 1.0F);
        // C.4 fault risk rises gently at the warning threshold and sharply
        // near critical condition. Fouling and the real C.3 heat/weather/dirt
        // exposure modulate a per-action declared maximum, but cannot make a
        // fresh, clean weapon fault from pure randomness.
        double operationalStress = Math.clamp(exposure.faultStress(), 1.0F, 2.0F);
        double chance = jam.getMaxChance() * conditionRisk * conditionRisk * conditionRisk
                * (0.15D + 0.85D * foulingRisk) * operationalStress;
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
        boolean serviceLockout = isServiceLockout(gun);
        boolean feedJam = !lockout && isFeedJammed(gun);
        Component state = lockout
                ? Component.translatable(serviceLockout
                        ? "tooltip.tacz.maintenance.service_fault" : "tooltip.tacz.maintenance.lockout")
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

    /** Restore the native heat component for old/Create-generated guns only when their loaded GunData declares it. */
    private static void ensureDeclaredHeatData(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun) || iGun.hasHeatData(gun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(gun);
        boolean declared = gunId != null && TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().hasHeatData())
                .orElse(false);
        if (declared) {
            iGun.setHeatAmount(gun, 0.0F);
        }
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

    /**
     * Capture heat and environmental exposure exclusively from server-visible
     * state. Rain is intentionally distinct from immersion/wet contact, so a
     * player under cover is not penalised merely because the dimension rains.
     */
    private record Exposure(float wearMultiplier, float foulingMultiplier, float faultStress) {
        private static Exposure capture(LivingEntity shooter, ItemStack gun, IndustryMaintenanceProfile profile) {
            IndustryMaintenanceProfile.OperationProfile operation = profile.getOperation();
            HeatStress heatStress = heatStress(gun, profile.getHeatStressMultiplier());
            float wear = operation.getWearMultiplier() * heatStress.wearMultiplier();
            float fouling = operation.getFoulingMultiplier() * heatStress.foulingMultiplier();
            float faultStress = Math.max(heatStress.wearMultiplier(), heatStress.foulingMultiplier());
            if (shooter != null) {
                boolean wetContact = shooter.isInWater() || touchesTaggedBlock(shooter, WET_EXPOSURE_BLOCKS);
                if (wetContact) {
                    wear *= operation.getSubmergedWearMultiplier();
                    fouling *= operation.getSubmergedFoulingMultiplier();
                    faultStress *= Math.max(operation.getSubmergedWearMultiplier(), operation.getSubmergedFoulingMultiplier());
                } else if (shooter.level().isRainingAt(shooter.blockPosition())) {
                    wear *= operation.getRainWearMultiplier();
                    fouling *= operation.getRainFoulingMultiplier();
                    faultStress *= Math.max(operation.getRainWearMultiplier(), operation.getRainFoulingMultiplier());
                }
                if (touchesTaggedBlock(shooter, CONTAMINANT_BLOCKS)) {
                    wear *= operation.getContaminantWearMultiplier();
                    fouling *= operation.getContaminantFoulingMultiplier();
                    faultStress *= Math.max(operation.getContaminantWearMultiplier(), operation.getContaminantFoulingMultiplier());
                }
            }
            return new Exposure(Math.clamp(wear, 0.0F, 16.0F), Math.clamp(fouling, 0.0F, 16.0F),
                    Math.clamp(faultStress, 1.0F, 4.0F));
        }

        /**
         * A profile multiplier is the maximum stress at full real HeatData,
         * never a fabricated heat value. Server configuration scales only the
         * excess above 1.0, so a scale of zero cleanly disables heat stress
         * without disabling normal maintenance accounting.
         */
        private static HeatStress heatStress(ItemStack gun, float configuredMaximum) {
            if (SyncConfig.INDUSTRY_HEAT_STRESS_ENABLED != null
                    && !SyncConfig.INDUSTRY_HEAT_STRESS_ENABLED.get()) {
                return HeatStress.NONE;
            }
            if (!(gun.getItem() instanceof IGun iGun) || !iGun.hasHeatData(gun)) {
                return HeatStress.NONE;
            }
            Identifier gunId = iGun.getGunId(gun);
            if (gunId == null) {
                return HeatStress.NONE;
            }
            return TimelessAPI.getCommonGunIndex(gunId)
                    .map(index -> index.getGunData().getHeatData())
                    .filter(heat -> heat != null && Float.isFinite(heat.getHeatMax()) && heat.getHeatMax() > 0.0F)
                    .map(heat -> {
                        float ratio = Math.clamp(iGun.getHeatAmount(gun) / heat.getHeatMax(), 0.0F, 1.0F);
                        float maximum = Math.clamp(Math.max(1.0F, configuredMaximum), 1.0F, 16.0F);
                        float excess = ratio * (maximum - 1.0F);
                        return new HeatStress(
                                1.0F + excess * configuredHeatScale(SyncConfig.INDUSTRY_HEAT_WEAR_SCALE),
                                1.0F + excess * configuredHeatScale(SyncConfig.INDUSTRY_HEAT_FOULING_SCALE)
                        );
                    }).orElse(HeatStress.NONE);
        }

        private static float configuredHeatScale(net.minecraftforge.common.ForgeConfigSpec.DoubleValue value) {
            if (value == null) {
                return 1.0F;
            }
            double raw = value.get();
            return Double.isFinite(raw) ? (float) Math.max(0.0D, Math.min(16.0D, raw)) : 1.0F;
        }

        private record HeatStress(float wearMultiplier, float foulingMultiplier) {
            private static final HeatStress NONE = new HeatStress(1.0F, 1.0F);
        }

        private static boolean touchesTaggedBlock(LivingEntity shooter, TagKey<Block> tag) {
            var position = shooter.blockPosition();
            return shooter.level().getBlockState(position).is(tag)
                    || shooter.level().getBlockState(position.below()).is(tag);
        }

        private int wear(int base) {
            return base <= 0 || wearMultiplier <= 0.0F ? 0 : Math.max(1, (int) Math.ceil(base * wearMultiplier));
        }

        private int fouling(int base) {
            return base <= 0 || foulingMultiplier <= 0.0F ? 0 : Math.max(1, (int) Math.ceil(base * foulingMultiplier));
        }
    }

    /** Result of one actual server-side round after maintenance accounting. */
    public record ShotOutcome(boolean recorded, boolean feedJammed, boolean serviceLocked) {
        public static final ShotOutcome NONE = new ShotOutcome(false, false, false);
        public static final ShotOutcome RECORDED = new ShotOutcome(true, false, false);
        public static final ShotOutcome FEED_JAMMED = new ShotOutcome(true, true, false);
        public static final ShotOutcome SERVICE_LOCKED = new ShotOutcome(true, false, true);

        /** Both C.2 and C.4 outcomes need an immediate authoritative held-stack snapshot. */
        public boolean faultCreated() {
            return feedJammed || serviceLocked;
        }
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
