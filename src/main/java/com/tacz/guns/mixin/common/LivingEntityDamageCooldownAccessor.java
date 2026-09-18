package com.tacz.guns.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@code LivingEntity#lastHurt}（protected，26.3 伤害闸门的另一半）。
 *
 * <p>见 {@link com.tacz.guns.util.DamageCooldownUtil} 的完整说明：26.3 的
 * {@code hurtServer} 用 {@code damageCooldownTime}（public）+ {@code lastHurt}
 * （protected）两个字段共同决定「这一发伤害吃不吃、吃多少」，
 * 清间隔必须两个都动。</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDamageCooldownAccessor {
    @Accessor("lastHurt")
    void tacz$setLastHurt(float lastHurt);
}
