package com.tacz.guns.industry.ammo;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Server-authoritative recoverable-case handling for industrial cartridges.
 *
 * <p>The stock gun pack's shell animation is purely client-side decoration.
 * This service is deliberately called only after {@code reduceAmmoOnce()} has
 * succeeded, so every spawned item represents one actually consumed round,
 * not an animation event, a rejected trigger pull, a shotgun pellet, or a
 * creative-mode free shot.  The resulting native {@link ItemEntity} merges,
 * despawns and can be collected by hoppers/players through vanilla mechanics.</p>
 */
public final class SpentCartridgeService {
    private static final int PICKUP_DELAY_TICKS = 10;

    private SpentCartridgeService() {
    }

    /**
     * Used on both logical sides to decide whether the legacy client-only shell
     * renderer should defer to the synchronised physical ItemEntity instead.
     */
    public static boolean hasRecoverableCase(Identifier ammoId) {
        return IndustryProfileManager.isCreateFlyProfileActive() && findDefinition(ammoId) != null;
    }

    /**
     * Spawn one real, collectible fired case from a successfully consumed
     * cartridge.  Ejection direction is deliberately independent of any
     * client model node so it stays server-authoritative for remote players,
     * automation and dedicated servers.
     */
    public static void ejectAfterFiring(LivingEntity shooter, Identifier ammoId) {
        if (shooter == null || shooter.level().isClientSide() || !IndustryProfileManager.isCreateFlyProfileActive()) {
            return;
        }
        CartridgeAssemblyDefinition definition = findDefinition(ammoId);
        if (definition == null) {
            return;
        }
        ItemStack spentCase = definition.createSpentCase();
        if (spentCase.isEmpty()) {
            return;
        }

        Vec3 forward = shooter.getLookAngle();
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        // The case clears the shooter's right side and arcs upward.  Small
        // server-side variation prevents a rapid burst from spawning every
        // entity in the same collision cell while retaining deterministic
        // physical pickup/recycling behaviour.
        double lateral = 0.30 + shooter.getRandom().nextDouble() * 0.08;
        Vec3 spawn = shooter.position()
                .add(right.scale(lateral))
                .add(forward.scale(0.10))
                .add(0.0, shooter.getEyeHeight() * 0.62, 0.0);
        Vec3 velocity = right.scale(0.13 + shooter.getRandom().nextDouble() * 0.07)
                .add(forward.scale((shooter.getRandom().nextDouble() - 0.5) * 0.06))
                .add(0.0, 0.12 + shooter.getRandom().nextDouble() * 0.06, 0.0);

        ItemEntity entity = new ItemEntity(shooter.level(), spawn.x, spawn.y, spawn.z, spentCase);
        entity.setPickUpDelay(PICKUP_DELAY_TICKS);
        entity.setDeltaMovement(velocity);
        shooter.level().addFreshEntity(entity);
    }

    @Nullable
    private static CartridgeAssemblyDefinition findDefinition(Identifier ammoId) {
        if (ammoId == null) {
            return null;
        }
        CartridgeAssemblyDefinition match = null;
        for (Map.Entry<Identifier, CartridgeAssemblyDefinition> entry
                : CommonAssetsManager.get().getAllCartridgeAssemblyRecipes()) {
            CartridgeAssemblyDefinition definition = entry.getValue();
            if (definition == null || !ammoId.equals(definition.getAmmo()) || !definition.ejectsCase()) {
                continue;
            }
            // A data pack may define more than one loading variant for an
            // AmmoId.  With no fired-case provenance in TACZ's loose ammo
            // stack, choosing one by map iteration would manufacture the
            // wrong case.  Fail closed until the pack author makes the ammo
            // identity unambiguous.
            if (match != null) {
                return null;
            }
            match = definition;
        }
        return match;
    }
}
