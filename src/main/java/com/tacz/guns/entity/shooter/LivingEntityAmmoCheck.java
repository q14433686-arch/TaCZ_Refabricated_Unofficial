package com.tacz.guns.entity.shooter;

import com.tacz.guns.config.common.GunConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Defines whether an entity must have ammunition available and whether a successful action consumes it.
 *
 * <p>Although this class lives in an internal shooter package, gameplay reaches these decisions through
 * {@code IGunOperator}. Its signatures and the distinction between the two policies are therefore a
 * semi-exposed compatibility surface. Ammunition sources answer <em>where</em> ammo is found or consumed;
 * this class only answers whether the current entity must perform those operations.</p>
 */
public class LivingEntityAmmoCheck {
    private final LivingEntity shooter;

    public LivingEntityAmmoCheck(LivingEntity shooter) {
        this.shooter = shooter;
    }

    /**
     * Returns whether ammunition availability is a prerequisite for the action.
     * Creative players historically bypass this prerequisite regardless of the consume-ammo setting.
     */
    public boolean needCheckAmmo() {
        if (shooter instanceof Player player) {
            return !player.isCreative();
        }
        return true;
    }

    /**
     * Returns whether ammunition is actually deducted after the action succeeds.
     * Unlike {@link #needCheckAmmo()}, creative players may consume ammo when
     * {@link GunConfig#CREATIVE_PLAYER_CONSUME_AMMO} is enabled; this difference is intentional.
     */
    public boolean consumesAmmoOrNot() {
        if (shooter instanceof Player player) {
            return !player.isCreative() || GunConfig.CREATIVE_PLAYER_CONSUME_AMMO.get();
        }
        return true;
    }
}
