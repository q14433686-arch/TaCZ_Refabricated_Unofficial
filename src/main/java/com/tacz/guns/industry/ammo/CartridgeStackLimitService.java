package com.tacz.guns.industry.ammo;

import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps calibre-bearing cases and projectile cores at the same effective stack
 * limit as their final loose-ammo product.
 *
 * <p>Fresh built-in Create outputs already carry {@code minecraft:max_stack_size}
 * from generated recipe JSON.  This service is the compatibility path for
 * cases made by older builds, data-pack outputs, or inventories that were
 * saved before those components existed.</p>
 */
public final class CartridgeStackLimitService {
    private static final String PLATFORM = "IndustryPlatform";
    private static final String PART_KIND = "IndustryPartKind";
    private static final String CALIBER = "CartridgeCaliber";
    private static final String PROJECTILE_TYPE = "ProjectileType";
    private static final String AMMO = "CartridgeAmmoId";

    private CartridgeStackLimitService() {
    }

    public static void normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        if (!"ammunition".equals(tag.getStringOr(PLATFORM, ""))) {
            return;
        }
        String partKind = tag.getStringOr(PART_KIND, "");
        if (!"case".equals(partKind) && !"spent_case".equals(partKind) && !"projectile".equals(partKind)) {
            return;
        }

        Identifier taggedAmmo = Identifier.tryParse(tag.getStringOr(AMMO, ""));
        if (taggedAmmo != null) {
            findByAmmo(taggedAmmo).ifPresent(definition -> definition.applyProductStackLimit(stack));
            return;
        }

        // Pre-stack-limit saves do not have CartridgeAmmoId. Resolve their
        // exact calibre/type against synchronized definitions only when that
        // identity is unambiguous; never choose a random data-pack variant.
        CartridgeAssemblyDefinition match = null;
        String caliber = tag.getStringOr(CALIBER, "");
        String projectileType = tag.getStringOr(PROJECTILE_TYPE, "");
        for (var entry : CommonAssetsManager.get().getAllCartridgeAssemblyRecipes()) {
            CartridgeAssemblyDefinition definition = entry.getValue();
            if (definition == null || !matches(partKind, caliber, projectileType, definition)) {
                continue;
            }
            if (match != null) {
                return;
            }
            match = definition;
        }
        if (match != null) {
            match.applyProductStackLimit(stack);
        }
    }

    private static java.util.Optional<CartridgeAssemblyDefinition> findByAmmo(Identifier ammoId) {
        CartridgeAssemblyDefinition match = null;
        for (var entry : CommonAssetsManager.get().getAllCartridgeAssemblyRecipes()) {
            CartridgeAssemblyDefinition definition = entry.getValue();
            if (definition == null || !ammoId.equals(definition.getAmmo())) {
                continue;
            }
            if (match != null) {
                return java.util.Optional.empty();
            }
            match = definition;
        }
        return java.util.Optional.ofNullable(match);
    }

    private static boolean matches(String partKind, String caliber, String projectileType,
                                   CartridgeAssemblyDefinition definition) {
        if ("projectile".equals(partKind)) {
            return caliber.equals(definition.getProjectileCaliber())
                    && projectileType.equals(definition.getProjectileType());
        }
        return caliber.equals(definition.getCaseCaliber());
    }
}
