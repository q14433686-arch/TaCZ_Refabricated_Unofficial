package com.tacz.guns.industry.magazine;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.ammo.AmmoProfileService;
import com.tacz.guns.industry.ammo.CartridgeStandardService;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves explicit removable-carrier interface standards for feed declarations.
 *
 * <p>Unregistered existing families remain isolated legacy declarations for
 * save/data-pack compatibility. Only a declaration that explicitly names a
 * valid standard can participate in the cross-native-AmmoId standard path.</p>
 */
public final class FeedInterfaceStandardService {
    private FeedInterfaceStandardService() {
    }

    @Nullable
    public static Identifier getStandardId(GunFeedDefinition definition) {
        if (definition == null || !definition.isValidExternalCarrierDefinition()) {
            return null;
        }
        Identifier requested = definition.getFeedStandardId();
        if (requested == null) {
            return null;
        }
        FeedInterfaceStandardDefinition standard = CommonAssetsManager.get().getFeedInterfaceStandard(requested);
        if (standard == null
                || standard.getMechanism() != definition.getMechanism()
                || !standard.getMagazineFamily().equals(definition.getMagazineFamily())) {
            return null;
        }
        Identifier cartridgeStandard = CartridgeStandardService.getStandardId(definition.getAmmoId());
        if (cartridgeStandard == null || !cartridgeStandard.equals(standard.getCartridgeStandard())) {
            return null;
        }
        for (ExternalCarrierVariant variant : definition.getExternalCarrierVariants()) {
            if (!standard.acceptsCapacity(variant.getCapacity())) {
                return null;
            }
        }
        return requested;
    }

    @Nullable
    public static FeedInterfaceStandardDefinition getStandard(GunFeedDefinition definition) {
        Identifier standardId = getStandardId(definition);
        return standardId == null ? null : CommonAssetsManager.get().getFeedInterfaceStandard(standardId);
    }

    /** Human-readable validation result used by GunFeedDefinitionManager. */
    @Nullable
    public static String validationFailure(GunFeedDefinition definition) {
        // In LEGACY the standard managers deliberately stay empty. Preserve
        // validated declarations for later profile reactivation rather than
        // logging false failures while physical magazines are disabled.
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return null;
        }
        if (definition == null || !definition.isValidExternalCarrierDefinition()) {
            return definition != null && definition.getFeedStandardId() != null
                    ? "feed_standard is only valid for detachable_magazine or belt"
                    : null;
        }
        Identifier requested = definition.getFeedStandardId();
        if (requested == null) {
            // Existing explicitly declared private families remain supported;
            // they simply cannot gain cross-native-AmmoId sharing by accident.
            return null;
        }
        FeedInterfaceStandardDefinition standard = CommonAssetsManager.get().getFeedInterfaceStandard(requested);
        if (standard == null) {
            return "feed_standard does not resolve to a loaded interface standard";
        }
        if (standard.getMechanism() != definition.getMechanism()) {
            return "feed_standard mechanism disagrees with gun_feed mechanism";
        }
        if (!standard.getMagazineFamily().equals(definition.getMagazineFamily())) {
            return "feed_standard magazine_family disagrees with gun_feed magazine_family";
        }
        Identifier cartridgeStandard = CartridgeStandardService.getStandardId(definition.getAmmoId());
        if (cartridgeStandard == null || !cartridgeStandard.equals(standard.getCartridgeStandard())) {
            return "feed_standard cartridge_standard disagrees with the declared ammo canonical standard";
        }
        for (ExternalCarrierVariant variant : definition.getExternalCarrierVariants()) {
            if (!standard.acceptsCapacity(variant.getCapacity())) {
                return "feed_standard does not declare carrier capacity " + variant.getCapacity();
            }
        }
        return null;
    }

    /** Both declarations must explicitly bind the same standard id. */
    public static boolean hasSameStandard(GunFeedDefinition first, GunFeedDefinition second) {
        Identifier firstId = getStandardId(first);
        Identifier secondId = getStandardId(second);
        return firstId != null && firstId.equals(secondId);
    }

    /**
     * Compatibility grouping for capacity selection and diagnostics. A named
     * standard rejects an explicitly conflicting standard id, but never removes
     * the older data-driven family + canonical-calibre route. That route already
     * requires both declarations and real AmmoIndex/profile identities; it is
     * not a GunIndex/model/name/capacity inference.
     */
    public static boolean hasSameCarrierInterface(GunFeedDefinition first, GunFeedDefinition second) {
        if (first == null || second == null
                || !first.isValidExternalCarrierDefinition() || !second.isValidExternalCarrierDefinition()
                || first.getMechanism() != second.getMechanism()
                || !first.getMagazineFamily().equals(second.getMagazineFamily())) {
            return false;
        }
        Identifier firstId = getStandardId(first);
        Identifier secondId = getStandardId(second);
        if (firstId != null && secondId != null && !firstId.equals(secondId)) {
            return false;
        }
        return AmmoProfileService.isLoadedAmmoIdentity(first.getAmmoId())
                && AmmoProfileService.isLoadedAmmoIdentity(second.getAmmoId())
                && AmmoProfileService.isSameCaliber(first.getAmmoId(), second.getAmmoId());
    }

    /**
     * Standard-bound carriers store the standard's canonical base AmmoId, not
     * whichever native AmmoId happened to create this particular receiver.
     */
    @Nullable
    public static Identifier getCanonicalAmmo(GunFeedDefinition definition) {
        FeedInterfaceStandardDefinition standard = getStandard(definition);
        return standard == null ? null : CartridgeStandardService.getCanonicalAmmo(standard.getCartridgeStandard());
    }
}
