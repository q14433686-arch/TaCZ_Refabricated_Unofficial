// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.common.molang;

import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding;
import net.minecraft.world.entity.LivingEntity;

/**
 * 代表一个生物实体。预置了一些常用查询。
 *
 * @param <T> 实体类型
 */
@SuppressWarnings("unused")
public class MolangLivingContext<T extends LivingEntity> extends MolangEntityContext<T> {

    public MolangLivingContext() {
    }

    public MolangLivingContext(T entity) {
        super(entity);
    }

    @QueryBinding("health")
    public double health() {
        T e = getEntity();
        return e != null ? (double) e.getHealth() : 0.0;
    }

    @QueryBinding("max_health")
    public double maxHealth() {
        T e = getEntity();
        return e != null ? (double) e.getMaxHealth() : 0.0;
    }

    @QueryBinding("health_ratio")
    public double healthRatio() {
        T e = getEntity();
        return e != null ? e.getHealth() / e.getMaxHealth() : 0.0;
    }

    @QueryBinding("is_baby")
    public double isBaby() {
        T e = getEntity();
        return e != null && e.isBaby() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sleeping")
    public double isSleeping() {
        T e = getEntity();
        return e != null && e.isSleeping() ? 1.0 : 0.0;
    }

    @QueryBinding("is_using_item")
    public double isUsingItem() {
        T e = getEntity();
        return e != null && e.isUsingItem() ? 1.0 : 0.0;
    }

    @QueryBinding("is_blocking")
    public double isBlocking() {
        T e = getEntity();
        return e != null && e.isBlocking() ? 1.0 : 0.0;
    }

    @QueryBinding("is_fall_flying")
    public double isFallFlying() {
        T e = getEntity();
        return e != null && e.isFallFlying() ? 1.0 : 0.0;
    }

    @QueryBinding("movement_speed")
    public double movementSpeed() {
        T e = getEntity();
        return e != null ? (double) e.getSpeed() : 0.0;
    }

    @QueryBinding("scale")
    public double scale() {
        T e = getEntity();
        return e != null ? (double) e.getScale() : 1.0;
    }

    @QueryBinding("armor_value")
    public double armorValue() {
        T e = getEntity();
        return e != null ? (double) e.getArmorValue() : 0.0;
    }

    @QueryBinding("arrow_count")
    public double arrowCount() {
        T e = getEntity();
        return e != null ? (double) e.getArrowCount() : 0.0;
    }

    @QueryBinding("is_hurt")
    public double isHurt() {
        T e = getEntity();
        return e != null && e.hurtDuration > 0 ? 1.0 : 0.0;
    }
}
