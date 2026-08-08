package com.tacz.guns.industry.ammo;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.industry.magazine.EnBlocClipService;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.industry.magazine.PhysicalMagazineService;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the exact profile of the physical round that the existing TACZ
 * state machine is about to consume. It does not alter timing or count rules;
 * it gives those existing authoritative mutations an exact AmmoId.
 */
public final class RoundProfileService {
    public static final String CHAMBER_AMMO_ID_TAG = "ChamberAmmoId";
    /** One script-side magazine removal awaiting a matching setAmmoInBarrel(true). */
    public static final String PENDING_CHAMBER_AMMO_ID_TAG = "PendingChamberAmmoId";

    private RoundProfileService() {
    }

    /**
     * Peek before {@code reduceAmmoOnce()}. The branch deliberately mirrors
     * ModernKineticGunScriptAPI's current bolt/count semantics exactly.
     */
    public static Identifier peekFiredAmmo(ItemStack gun, GunData gunData) {
        if (!(gun.getItem() instanceof IGun iGun) || gunData == null) {
            return DefaultAssets.EMPTY_AMMO_ID;
        }
        Identifier base = gunData.getAmmoId();
        Bolt bolt = gunData.getBolt();
        if (bolt == null) {
            return base;
        }
        boolean chambered = bolt != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(gun);
        boolean storageRound = switch (bolt) {
            case MANUAL_ACTION -> false;
            case CLOSED_BOLT, OPEN_BOLT -> iGun.getCurrentAmmoCount(gun) > 0;
            default -> false;
        };
        if (storageRound) {
            Identifier physical = peekStorageRound(gun);
            return physical.equals(DefaultAssets.EMPTY_AMMO_ID) ? base : physical;
        }
        if (chambered) {
            Identifier chamber = getChamberAmmoId(gun);
            return chamber.equals(DefaultAssets.EMPTY_AMMO_ID) ? base : chamber;
        }
        return base;
    }

    /** Save the exact round when a profile-aware service moves it into the chamber. */
    public static void setChamberAmmoId(ItemStack gun, Identifier ammoId) {
        ItemNbtUtils.updateTag(gun, tag -> {
            if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                tag.remove(CHAMBER_AMMO_ID_TAG);
            } else {
                tag.putString(CHAMBER_AMMO_ID_TAG, ammoId.toString());
            }
        });
    }

    public static Identifier getChamberAmmoId(ItemStack gun) {
        Identifier parsed = Identifier.tryParse(ItemNbtUtils.getTag(gun).getStringOr(CHAMBER_AMMO_ID_TAG, ""));
        return parsed == null ? DefaultAssets.EMPTY_AMMO_ID : parsed;
    }

    /** Save one script-removed physical round until its actual chamber feed point. */
    public static void setPendingChamberAmmoId(ItemStack gun, Identifier ammoId) {
        ItemNbtUtils.updateTag(gun, tag -> {
            if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                tag.remove(PENDING_CHAMBER_AMMO_ID_TAG);
            } else {
                tag.putString(PENDING_CHAMBER_AMMO_ID_TAG, ammoId.toString());
            }
        });
    }

    /** Promote a pending physical profile only when the legacy script reaches its real chamber call. */
    public static void promotePendingChamberAmmoId(ItemStack gun) {
        Identifier pending = Identifier.tryParse(ItemNbtUtils.getTag(gun).getStringOr(PENDING_CHAMBER_AMMO_ID_TAG, ""));
        if (pending != null && !DefaultAssets.EMPTY_AMMO_ID.equals(pending)) {
            setChamberAmmoId(gun, pending);
        }
        setPendingChamberAmmoId(gun, DefaultAssets.EMPTY_AMMO_ID);
    }

    /** Clear only after a real state transition has left no chambered round. */
    public static void clearChamberIfEmpty(ItemStack gun) {
        if (gun.getItem() instanceof IGun iGun && !iGun.hasBulletInBarrel(gun)) {
            setChamberAmmoId(gun, DefaultAssets.EMPTY_AMMO_ID);
            setPendingChamberAmmoId(gun, DefaultAssets.EMPTY_AMMO_ID);
        }
    }

    private static Identifier peekStorageRound(ItemStack gun) {
        if (gun.getItem() instanceof IGun iGun) {
            ItemStack installed = iGun.getInstalledMagazine(gun);
            if (installed.getItem() instanceof MagazineItemDataAccessor magazine) {
                return magazine.getNextRoundAmmoId(installed);
            }
        }
        if (EnBlocClipService.hasActiveInstalledClip(gun)) {
            ItemStack clip = EnBlocClipService.getInstalledClip(gun);
            if (clip.getItem() instanceof MagazineItemDataAccessor magazine) {
                return magazine.getNextRoundAmmoId(clip);
            }
        }
        Identifier internal = com.tacz.guns.industry.magazine.InternalFeedService.getNextRoundAmmoId(gun);
        return internal.equals(DefaultAssets.EMPTY_AMMO_ID) ? DefaultAssets.EMPTY_AMMO_ID : internal;
    }
}
