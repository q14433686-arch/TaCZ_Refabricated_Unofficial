package com.tacz.guns.industry.maintenance;

import com.google.gson.annotations.SerializedName;

/**
 * Data-driven, non-jamming maintenance baseline for one GunId.
 *
 * <p>This profile only controls persistent Condition/Fouling accounting in
 * phase A. The {@code jam} object is retained as declared future service data,
 * but no random jam, lockout, shoot rejection, or ammo mutation is enabled by
 * this class or {@link IndustryMaintenanceService}.</p>
 */
public final class IndustryMaintenanceProfile {
    public static final int MAX_PER_SHOT_WEAR = 1_000;
    public static final int MAX_FOULING_PER_SHOT = 1_000;

    @SerializedName("schema_version")
    private int schemaVersion = 1;

    @SerializedName("eligibility")
    private String eligibility = "industrial_assembly";

    @SerializedName("maintenance_class")
    private String maintenanceClass = "surveyed";

    @SerializedName("wear_per_shot")
    private WearPerShot wearPerShot = new WearPerShot();

    @SerializedName("fouling_per_shot")
    private int foulingPerShot = 3;

    @SerializedName("heat_stress_multiplier")
    private float heatStressMultiplier = 1.0F;

    /** Declared for phase C only; phase A does not inspect it for shoot gating. */
    @SerializedName("jam")
    private JamThresholds jam = new JamThresholds();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getEligibility() {
        return eligibility == null ? "" : eligibility;
    }

    public String getMaintenanceClass() {
        return maintenanceClass == null ? "surveyed" : maintenanceClass;
    }

    public WearPerShot getWearPerShot() {
        return wearPerShot == null ? new WearPerShot() : wearPerShot;
    }

    public int getFoulingPerShot() {
        return Math.clamp(foulingPerShot, 0, MAX_FOULING_PER_SHOT);
    }

    public float getHeatStressMultiplier() {
        return Float.isFinite(heatStressMultiplier) ? Math.clamp(heatStressMultiplier, 0.0F, 16.0F) : 1.0F;
    }

    public JamThresholds getJam() {
        return jam == null ? new JamThresholds() : jam;
    }

    public boolean isValid() {
        return schemaVersion == 1
                && "industrial_assembly".equals(getEligibility())
                && !getMaintenanceClass().isBlank()
                && getWearPerShot().isValid()
                && foulingPerShot >= 0 && foulingPerShot <= MAX_FOULING_PER_SHOT
                && Float.isFinite(heatStressMultiplier) && heatStressMultiplier >= 0.0F
                && getJam().isValid();
    }

    /** Conservative generic profile used only for explicitly marked surveyed/all-gun paths. */
    public static IndustryMaintenanceProfile genericSurveyed() {
        return new IndustryMaintenanceProfile();
    }

    public static final class WearPerShot {
        @SerializedName("receiver")
        private int receiver = 1;
        @SerializedName("bolt")
        private int bolt = 2;
        @SerializedName("barrel")
        private int barrel = 3;
        @SerializedName("trigger")
        private int trigger = 1;
        @SerializedName("recoil")
        private int recoil = 2;

        public int getReceiver() {
            return clampWear(receiver);
        }

        public int getBolt() {
            return clampWear(bolt);
        }

        public int getBarrel() {
            return clampWear(barrel);
        }

        public int getTrigger() {
            return clampWear(trigger);
        }

        public int getRecoil() {
            return clampWear(recoil);
        }

        private boolean isValid() {
            return receiver >= 0 && receiver <= MAX_PER_SHOT_WEAR
                    && bolt >= 0 && bolt <= MAX_PER_SHOT_WEAR
                    && barrel >= 0 && barrel <= MAX_PER_SHOT_WEAR
                    && trigger >= 0 && trigger <= MAX_PER_SHOT_WEAR
                    && recoil >= 0 && recoil <= MAX_PER_SHOT_WEAR;
        }

        private static int clampWear(int value) {
            return Math.clamp(value, 0, MAX_PER_SHOT_WEAR);
        }
    }

    /** Stored and synchronised now so future service data does not need an incompatible schema migration. */
    public static final class JamThresholds {
        @SerializedName("warning_condition")
        private int warningCondition = 6_000;
        @SerializedName("critical_condition")
        private int criticalCondition = 1_500;
        @SerializedName("max_chance")
        private float maxChance = 0.08F;

        public int getWarningCondition() {
            return Math.clamp(warningCondition, 0, IndustryMaintenanceService.MAX_CONDITION);
        }

        public int getCriticalCondition() {
            return Math.clamp(criticalCondition, 0, IndustryMaintenanceService.MAX_CONDITION);
        }

        public float getMaxChance() {
            return Float.isFinite(maxChance) ? Math.clamp(maxChance, 0.0F, 1.0F) : 0.0F;
        }

        private boolean isValid() {
            return warningCondition >= 0 && warningCondition <= IndustryMaintenanceService.MAX_CONDITION
                    && criticalCondition >= 0 && criticalCondition <= warningCondition
                    && Float.isFinite(maxChance) && maxChance >= 0.0F && maxChance <= 1.0F;
        }
    }
}
