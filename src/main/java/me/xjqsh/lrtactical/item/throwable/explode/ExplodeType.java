package me.xjqsh.lrtactical.item.throwable.explode;

import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「爆炸手雷」类型 —— 把 {@link ExplodeThrowableData} 的解析与 {@link GrenadeEntity} 的创建绑定。
 *
 * <h2>26.2 移植说明</h2>
 * 上游在 {@code createEntity} 里用 {@code ServerConfig.CROUCHING_INIT_SPEED_PERCENT}
 * 让潜行投掷降速。配置层（NeoForge {@code ModConfigSpec}）尚未移植，
 * 此处用常量 {@link #CROUCHING_SPEED_FACTOR} 代替，取值与上游默认值一致。
 * <b>不是省略该行为</b>，只是暂时不可配置。
 */
public final class ExplodeType {
    /** 潜行时的初速倍率，与上游 {@code ServerConfig} 默认值一致。 */
    private static final float CROUCHING_SPEED_FACTOR = 0.5F;

    public static final ThrowableType<ExplodeThrowableData, GrenadeEntity> EXPLODE =
            ThrowableType.Builder.<ExplodeThrowableData, GrenadeEntity>of()
                    .setFactory(ExplodeType::createEntity)
                    .setSerializer(json -> CommonAssetsManager.GSON.fromJson(json, ExplodeThrowableData.class))
                    .build();

    private ExplodeType() {
    }

    public static GrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, ExplodeThrowableData data) {
        GrenadeEntity entity = new GrenadeEntity(thrower, thrower.level(), data.getEntityData().getLifeTime());

        float initialSpeed = (float) data.getInitialSpeed();
        if (thrower.isCrouching()) {
            initialSpeed *= CROUCHING_SPEED_FACTOR;
        }
        // 按投掷者视角方向抛出
        entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
        entity.setItem(stack);

        entity.setBaseData(data.getEntityData());

        ExplodeThrowableData.ExplodeData explode = data.getExplode();
        entity.setDamage(explode.getDamage());
        entity.setRadius(explode.getRadius());
        entity.setDestroyBlocks(explode.isDestroyBlocks());
        entity.setExplodeDestroyMultiplier(explode.getDestroyMultiplier());
        entity.setTriggerOnExplode(explode.isTriggerOnExplode());
        entity.setRemoteDetonation(explode.isRemoteDetonation());
        entity.setScreenShakeTime(explode.getScreenShakeTime());
        entity.setScreenShakeAmplitude(explode.getScreenShakeAmplitude());

        return entity;
    }
}
