package me.xjqsh.lrtactical.item.throwable.smoke;

import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「烟雾弹」类型。
 *
 * <p>是四种投掷物里数据最简单的一个：<b>直接用基础 {@link ThrowableData}</b>，
 * 没有自己的 data 子类（上游亦然）。烟雾的浓度与范围目前是代码常量
 * （见 {@link SmokeGrenadeEntity}），持续时间由 {@code entity.life_time} 控制。
 */
public final class SmokeType {
    /** 潜行时的初速倍率，与其余类型保持一致。 */
    private static final float CROUCHING_SPEED_FACTOR = 0.5F;

    public static final ThrowableType<ThrowableData, SmokeGrenadeEntity> SMOKE =
            ThrowableType.Builder.<ThrowableData, SmokeGrenadeEntity>of()
                    .setFactory(SmokeType::createEntity)
                    .setSerializer(json -> CommonAssetsManager.GSON.fromJson(json, ThrowableData.class))
                    .build();

    private SmokeType() {
    }

    public static SmokeGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, ThrowableData data) {
        SmokeGrenadeEntity entity =
                new SmokeGrenadeEntity(thrower, thrower.level(), data.getEntityData().getLifeTime());

        float initialSpeed = (float) data.getInitialSpeed();
        if (thrower.isCrouching()) {
            initialSpeed *= CROUCHING_SPEED_FACTOR;
        }
        entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
        entity.setItem(stack);
        entity.setBaseData(data.getEntityData());
        return entity;
    }
}
