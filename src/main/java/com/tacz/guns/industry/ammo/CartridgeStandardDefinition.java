package com.tacz.guns.industry.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

/**
 * One named, data-driven cartridge dimensional standard.
 *
 * <p>The resource id identifies a stable industrial standard (for example
 * {@code tacz:556x45}), while {@code canonical_ammo} identifies the base
 * loose-ammo identity measured by its gauge, case and projectile routes. It
 * is deliberately separate from an individual {@link AmmoProfileDefinition}:
 * FMJ/AP/HP/slug remain distinct physical rounds under the same standard.</p>
 */
public final class CartridgeStandardDefinition {
    public static final int SCHEMA_VERSION = 1;

    @SerializedName("schema_version")
    private int schemaVersion = SCHEMA_VERSION;
    @SerializedName("canonical_ammo")
    private Identifier canonicalAmmo;
    @SerializedName("cartridge_caliber")
    private String cartridgeCaliber = "";

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Identifier getCanonicalAmmo() {
        return canonicalAmmo;
    }

    public String getCartridgeCaliber() {
        return cartridgeCaliber == null ? "" : cartridgeCaliber;
    }

    public boolean isValid() {
        return schemaVersion == SCHEMA_VERSION
                && canonicalAmmo != null
                && !getCartridgeCaliber().isBlank();
    }
}
