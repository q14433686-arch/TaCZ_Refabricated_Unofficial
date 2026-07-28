package me.xjqsh.lrtactical.item.throwable.flash;

import me.xjqsh.lrtactical.entity.StunGrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「闪光弹」类型。
 */
public final class StunType {
    /** 潜行时的初速倍率，与其余类型保持一致。 */
    private static final float CROUCHING_SPEED_FACTOR = 0.5F;

    public static final ThrowableType<StunThrowableData, StunGrenadeEntity> STUN =
            ThrowableType.Builder.<StunThrowableData, StunGrenadeEntity>of()
                    .setFactory(StunType::createEntity)
                    .setSerializer(json -> CommonAssetsManager.GSON.fromJson(json, StunThrowableData.class))
                    .build();

    private StunType() {
    }

    public static StunGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, StunThrowableData data) {
        StunGrenadeEntity entity =
                new StunGrenadeEntity(thrower, thrower.level(), data.getEntityData().getLifeTime());

        float initialSpeed = (float) data.getInitialSpeed();
        if (thrower.isCrouching()) {
            initialSpeed *= CROUCHING_SPEED_FACTOR;
        }
        entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
        entity.setItem(stack);
        entity.setBaseData(data.getEntityData());
        entity.setStunData(data.getStunData());
        return entity;
    }
}
