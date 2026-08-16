package com.tacz.guns.api.item.ammo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A replaceable source of physical ammunition for a living entity.
 *
 * <p>Implementations may bridge inventories owned by another mod. The same implementation can be
 * used on both logical sides: {@link #hasAmmo(LivingEntity, ItemStack)} is also queried by client
 * animation and prediction code, while {@link #consumeAmmo(LivingEntity, ItemStack, int)} is called
 * by authoritative gameplay code.</p>
 *
 * <p>Dummy ammunition stored on the gun and creative/infinite-ammo rules are handled by TaCZ before
 * this source is queried.</p>
 */
public interface AmmoSource {
    /**
     * Checks whether this source contains at least one round compatible with {@code gunItem}.
     * This method must not mutate the source.
     */
    boolean hasAmmo(LivingEntity shooter, ItemStack gunItem);

    /**
     * Consumes up to {@code requestedAmount} compatible rounds.
     *
     * @return the number of rounds consumed, between {@code 0} and {@code requestedAmount}
     */
    int consumeAmmo(LivingEntity shooter, ItemStack gunItem, int requestedAmount);
}
