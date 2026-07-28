package me.xjqsh.lrtactical.item.throwable.explode;

import me.xjqsh.lrtactical.entity.StickyGrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「粘性手雷」类型 —— 与 {@link ExplodeType} 共用 {@link ExplodeThrowableData}。
 *
 * <p>粘性雷在数据层与爆炸雷完全一致（伤害/半径/破坏方块/连锁引爆都通用），
 * 差异只在实体行为（粘附而非弹跳），因此<b>不需要新的 data 类</b>。
 * 这也是上游的做法。
 *
 * <p>唯一的注意点：{@code entity.setShouldBounce(...)} <b>不要设</b> ——
 * {@link StickyGrenadeEntity} 完全覆写了 {@code doMultiBounce} 与 {@code onHit}，
 * 弹跳开关对它不起作用；而基类的 {@code shouldBounce=false} 语义是
 * 「一碰就 onDeath（炸）」，与粘附相冲突。详见该实体类的注释。
 */
public final class StickyType {
    /** 潜行时的初速倍率，与 {@link ExplodeType} 保持一致。 */
    private static final float CROUCHING_SPEED_FACTOR = 0.5F;

    public static final ThrowableType<ExplodeThrowableData, StickyGrenadeEntity> STICKY =
            ThrowableType.Builder.<ExplodeThrowableData, StickyGrenadeEntity>of()
                    .setFactory(StickyType::createEntity)
                    .setSerializer(json -> CommonAssetsManager.GSON.fromJson(json, ExplodeThrowableData.class))
                    .build();

    private StickyType() {
    }

    public static StickyGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, ExplodeThrowableData data) {
        StickyGrenadeEntity entity =
                new StickyGrenadeEntity(thrower, thrower.level(), data.getEntityData().getLifeTime());

        float initialSpeed = (float) data.getInitialSpeed();
        if (thrower.isCrouching()) {
            initialSpeed *= CROUCHING_SPEED_FACTOR;
        }
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
