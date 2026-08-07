package com.tacz.guns.industry.maintenance;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Server-authoritative phase-A maintenance state.
 *
 * <p>This service deliberately records only component Condition, Fouling and
 * shot count. It does not reject shots, create a jam, mutate ammunition, or
 * consume maintenance materials. Those mechanics require the separate,
 * animation-backed clear-jam and service-bench transactions planned for later
 * phases.</p>
 */
public final class IndustryMaintenanceService {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONDITION = 10_000;

    public static final String SCHEMA_TAG = "IndustryMaintenanceSchema";
    public static final String RECEIVER_TAG = "IndustryConditionReceiver";
    public static final String BOLT_TAG = "IndustryConditionBolt";
    public static final String BARREL_TAG = "IndustryConditionBarrel";
    public static final String TRIGGER_TAG = "IndustryConditionTrigger";
    public static final String RECOIL_TAG = "IndustryConditionRecoil";
    public static final String FOULING_TAG = "IndustryFouling";
    public static final String SEED_TAG = "IndustryMaintenanceSeed";
    public static final String SHOTS_TAG = "IndustryMaintenanceShots";

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
     * Called only after {@code reduceAmmoOnce()} succeeded. Phase A records the
     * event but never changes its result, preserving current shooting semantics.
     */
    public static boolean recordSuccessfulShot(ItemStack gun) {
        if (!migrateIfEligible(gun)) {
            return false;
        }
        IndustryMaintenanceProfile profile = getProfileFor(gun);
        if (profile == null) {
            return false;
        }
        IndustryMaintenanceProfile.WearPerShot wear = profile.getWearPerShot();
        ItemNbtUtils.updateTag(gun, tag -> {
            migrateTag(tag);
            tag.putInt(RECEIVER_TAG, subtractWear(tag.getIntOr(RECEIVER_TAG, MAX_CONDITION), wear.getReceiver()));
            tag.putInt(BOLT_TAG, subtractWear(tag.getIntOr(BOLT_TAG, MAX_CONDITION), wear.getBolt()));
            tag.putInt(BARREL_TAG, subtractWear(tag.getIntOr(BARREL_TAG, MAX_CONDITION), wear.getBarrel()));
            tag.putInt(TRIGGER_TAG, subtractWear(tag.getIntOr(TRIGGER_TAG, MAX_CONDITION), wear.getTrigger()));
            tag.putInt(RECOIL_TAG, subtractWear(tag.getIntOr(RECOIL_TAG, MAX_CONDITION), wear.getRecoil()));
            tag.putInt(FOULING_TAG, clampCondition(tag.getIntOr(FOULING_TAG, 0) + profile.getFoulingPerShot()));
            long oldShots = Math.max(0L, tag.getLongOr(SHOTS_TAG, 0L));
            tag.putLong(SHOTS_TAG, oldShots == Long.MAX_VALUE ? Long.MAX_VALUE : oldShots + 1L);
        });
        return true;
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
        return Component.translatable("tooltip.tacz.maintenance.status",
                        Component.translatable(snapshot.status().translationKey()), condition, fouling)
                .withStyle(style -> style.withColor(snapshot.status().color()));
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
        return String.format(Locale.ROOT, "%.0f%%", clampCondition(amount) * 100.0D / MAX_CONDITION);
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
