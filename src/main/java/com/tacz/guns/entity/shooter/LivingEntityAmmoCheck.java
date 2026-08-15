package com.tacz.guns.entity.shooter;

import com.tacz.guns.config.common.GunConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 「是否需要检查/消耗弹药」的决策点。
 *
 * <p>虽然此类位于内部包 {@code entity.shooter}，但它承载的是全服最核心的弹药决策语义，
 * 并且通过 {@code IGunOperator#needCheckAmmo()} / {@code IGunOperator#consumesAmmoOrNot()}
 * 对外暴露——因此本质上是<b>半暴露的隐性 API</b>。下游模组若要改变「何时需要检查/消耗弹药」
 * 的判定，最自然的做法就是 mixin 这两个方法。</p>
 *
 * <p>注意：{@link #needCheckAmmo()} 与 {@link #consumesAmmoOrNot()} 语义不同——
 * 前者决定「要不要做弹药检查」（创造模式默认不检查），后者决定「开火是否真的扣弹药」
 * （创造模式默认不扣，除非 {@code GunConfig.CREATIVE_PLAYER_CONSUME_AMMO} 开启）。
 * 两者在 {@code ModernKineticGunScriptAPI} 与 {@code LivingEntityShoot} 中分别被调用，
 * 请勿混淆。</p>
 */
public class LivingEntityAmmoCheck {
    private final LivingEntity shooter;

    public LivingEntityAmmoCheck(LivingEntity shooter) {
        this.shooter = shooter;
    }

    public boolean needCheckAmmo() {
        if (shooter instanceof Player player) {
            return !player.isCreative();
        }
        return true;
    }

    public boolean consumesAmmoOrNot() {
        if (shooter instanceof Player player) {
            return !player.isCreative() || GunConfig.CREATIVE_PLAYER_CONSUME_AMMO.get();
        }
        return true;
    }
}
