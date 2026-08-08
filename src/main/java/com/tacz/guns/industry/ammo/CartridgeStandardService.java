package com.tacz.guns.industry.ammo;

import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Shared resolver for explicit cartridge dimensional standards. */
public final class CartridgeStandardService {
    private CartridgeStandardService() {
    }

    /** Resolve an exact AmmoId through its explicit profile alias to a named standard. */
    @Nullable
    public static Identifier getStandardId(Identifier ammoId) {
        if (ammoId == null) {
            return null;
        }
        Identifier canonicalAmmo = AmmoProfileService.canonicalCaliber(ammoId);
        return CommonAssetsManager.get().getCartridgeStandardIdForCanonicalAmmo(canonicalAmmo);
    }

    @Nullable
    public static CartridgeStandardDefinition getStandard(Identifier ammoId) {
        Identifier standardId = getStandardId(ammoId);
        return standardId == null ? null : CommonAssetsManager.get().getCartridgeStandard(standardId);
    }

    @Nullable
    public static CartridgeStandardDefinition getStandardById(Identifier standardId) {
        return CommonAssetsManager.get().getCartridgeStandard(standardId);
    }

    /**
     * True only when both identities resolve through loaded explicit cartridge
     * standards to exactly the same standard resource id.
     */
    public static boolean isSameStandard(Identifier firstAmmo, Identifier secondAmmo) {
        Identifier first = getStandardId(firstAmmo);
        Identifier second = getStandardId(secondAmmo);
        return first != null && first.equals(second);
    }

    @Nullable
    public static Identifier getCanonicalAmmo(Identifier standardId) {
        CartridgeStandardDefinition standard = getStandardById(standardId);
        return standard == null ? null : standard.getCanonicalAmmo();
    }
}
