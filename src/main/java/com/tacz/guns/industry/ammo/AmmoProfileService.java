package com.tacz.guns.industry.ammo;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;

/**
 * Shared profile/canonical-calibre resolver used by inventory validation,
 * physical-carrier queues, reload routes and server projectile creation.
 */
public final class AmmoProfileService {
    private AmmoProfileService() {
    }

    /** Explicit JSON wins; an existing base AmmoId receives a neutral FMJ profile. */
    public static AmmoProfileDefinition resolve(Identifier ammoId) {
        if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            return AmmoProfileDefinition.standard(DefaultAssets.EMPTY_AMMO_ID);
        }
        AmmoProfileDefinition profile = CommonAssetsManager.get().getAmmoProfile(ammoId);
        return profile == null ? AmmoProfileDefinition.standard(ammoId) : profile;
    }

    public static Identifier canonicalCaliber(Identifier ammoId) {
        return resolve(ammoId).getCaliberAmmoId();
    }

    /**
     * Same-carrier compatibility is only a canonical-calibre comparison after
     * each identity has resolved through an explicit profile (or its own base
     * identity). No gun name, GunIndex class or visual resource is consulted.
     */
    public static boolean isSameCaliber(Identifier first, Identifier second) {
        if (first == null || second == null
                || DefaultAssets.EMPTY_AMMO_ID.equals(first) || DefaultAssets.EMPTY_AMMO_ID.equals(second)) {
            return false;
        }
        return canonicalCaliber(first).equals(canonicalCaliber(second));
    }

    /** A declared alternate is valid only if its exact AmmoIndex is loaded. */
    public static boolean isLoadedAmmoIdentity(Identifier ammoId) {
        return ammoId != null && CommonAssetsManager.get().getAmmoIndex(ammoId) != null;
    }
}
