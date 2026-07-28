package me.xjqsh.lrtactical.item.throwable.area;

import me.xjqsh.lrtactical.entity.EffectCloudGrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「效果云」类型 —— 药水云 / 喷溅弹。
 */
public final class CloudType {
    /** 潜行时的初速倍率，与其余类型保持一致。 */
    private static final float CROUCHING_SPEED_FACTOR = 0.5F;

    public static final ThrowableType<EffectCloudThrowableData, EffectCloudGrenadeEntity> EFFECT_CLOUD =
            ThrowableType.Builder.<EffectCloudThrowableData, EffectCloudGrenadeEntity>of()
                    .setFactory(CloudType::createEntity)
                    .setSerializer(json -> CommonAssetsManager.GSON.fromJson(json, EffectCloudThrowableData.class))
                    .build();

    private CloudType() {
    }

    public static EffectCloudGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower,
                                                        EffectCloudThrowableData data) {
        EffectCloudGrenadeEntity entity =
                new EffectCloudGrenadeEntity(thrower, thrower.level(), data.getEntityData().getLifeTime());

        float initialSpeed = (float) data.getInitialSpeed();
        if (thrower.isCrouching()) {
            initialSpeed *= CROUCHING_SPEED_FACTOR;
        }
        entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
        entity.setItem(stack);
        entity.setBaseData(data.getEntityData());
        entity.setCloudData(data.getCloudData());
        return entity;
    }
}
