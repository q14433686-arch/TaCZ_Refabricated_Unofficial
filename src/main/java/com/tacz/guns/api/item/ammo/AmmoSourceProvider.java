package com.tacz.guns.api.item.ammo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Selects a custom {@link AmmoSource} for an entity and gun.
 *
 * <p>Return {@code null} when the provider does not own the entity's ammunition. Providers are
 * consulted in registration order and the first non-null source wins.</p>
 */
@FunctionalInterface
public interface AmmoSourceProvider {
    @Nullable
    AmmoSource findAmmoSource(LivingEntity shooter, ItemStack gunItem);
}
