package com.tacz.guns.industry.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

/**
 * Server-authoritative ballistic profile for one independently addressable
 * loose-ammo identity.
 *
 * <p>{@code caliber_ammo} is the canonical chamber/carrier identity. It is
 * deliberately separate from {@code ammo}: an AP, HP, or slug round can be a
 * distinct ItemStack and projectile while still being physically compatible
 * with a carrier made for the same calibre.</p>
 */
public final class AmmoProfileDefinition {
    public static final int SCHEMA_VERSION = 1;

    @SerializedName("schema_version")
    private int schemaVersion = SCHEMA_VERSION;
    private Identifier ammo;
    @SerializedName("caliber_ammo")
    private Identifier caliberAmmo;
    private String kind = "fmj";
    @SerializedName("damage_multiplier")
    private float damageMultiplier = 1.0F;
    @SerializedName("armor_ignore_multiplier")
    private float armorIgnoreMultiplier = 1.0F;
    @SerializedName("armor_ignore_addend")
    private float armorIgnoreAddend = 0.0F;
    @SerializedName("pierce_add")
    private int pierceAdd;
    /** Zero means retain the gun/bullet's normal pierce count. */
    @SerializedName("pierce_override")
    private int pierceOverride;
    @SerializedName("speed_multiplier")
    private float speedMultiplier = 1.0F;
    /** Zero means retain the normal GunData/BulletData projectile count. */
    @SerializedName("projectile_count_override")
    private int projectileCountOverride;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Identifier getAmmoId() {
        return ammo;
    }

    public Identifier getCaliberAmmoId() {
        return caliberAmmo;
    }

    public String getKind() {
        return kind == null || kind.isBlank() ? "fmj" : kind;
    }

    public float getDamageMultiplier() {
        return clampMultiplier(damageMultiplier);
    }

    public float getArmorIgnoreMultiplier() {
        return clampMultiplier(armorIgnoreMultiplier);
    }

    public float getArmorIgnoreAddend() {
        return Float.isFinite(armorIgnoreAddend) ? Math.clamp(armorIgnoreAddend, -1.0F, 1.0F) : 0.0F;
    }

    public int getPierceAdd() {
        return Math.clamp(pierceAdd, -32, 32);
    }

    public int getPierceOverride() {
        return Math.clamp(pierceOverride, 0, 64);
    }

    public float getSpeedMultiplier() {
        return clampMultiplier(speedMultiplier);
    }

    public int getProjectileCountOverride() {
        return Math.clamp(projectileCountOverride, 0, 128);
    }

    public boolean isValid() {
        return schemaVersion == SCHEMA_VERSION
                && ammo != null && caliberAmmo != null
                && kind != null && !kind.isBlank()
                && validMultiplier(damageMultiplier)
                && validMultiplier(armorIgnoreMultiplier)
                && Float.isFinite(armorIgnoreAddend) && armorIgnoreAddend >= -1.0F && armorIgnoreAddend <= 1.0F
                && pierceAdd >= -32 && pierceAdd <= 32
                && pierceOverride >= 0 && pierceOverride <= 64
                && validMultiplier(speedMultiplier)
                && projectileCountOverride >= 0 && projectileCountOverride <= 128;
    }

    /**
     * Base ammo identities remain valid without a profile JSON. The implicit
     * profile preserves historical TACZ behaviour while alternate AmmoIds need
     * an explicit data declaration before they become same-calibre compatible.
     */
    public static AmmoProfileDefinition standard(Identifier ammoId) {
        AmmoProfileDefinition definition = new AmmoProfileDefinition();
        definition.ammo = ammoId;
        definition.caliberAmmo = ammoId;
        definition.kind = "fmj";
        return definition;
    }

    private static boolean validMultiplier(float value) {
        return Float.isFinite(value) && value >= 0.0F && value <= 16.0F;
    }

    private static float clampMultiplier(float value) {
        return validMultiplier(value) ? value : 1.0F;
    }
}
