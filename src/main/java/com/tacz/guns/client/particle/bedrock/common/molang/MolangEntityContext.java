// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.common.molang;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding;
import net.minecraft.world.entity.Entity;

/**
 * 代表一个实体。预置了一些常用查询。
 *
 * @param <T> 实体类型
 */
@SuppressWarnings("unused")
public class MolangEntityContext<T extends Entity> extends MolangContext<T> {

    public MolangEntityContext() {
    }

    public MolangEntityContext(T entity) {
        super(entity);
    }

    // ==================== Entity 状态 ====================

    @QueryBinding("is_on_ground")
    public double isOnGround() {
        T e = getEntity();
        return e != null && e.onGround() ? 1.0 : 0.0;
    }

    @QueryBinding("is_alive")
    public double isAlive() {
        T e = getEntity();
        return e != null && e.isAlive() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_water")
    public double isInWater() {
        T e = getEntity();
        return e != null && e.isInWater() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_water_or_rain")
    public double isInWaterOrRain() {
        T e = getEntity();
        return e != null && e.isInWaterRainOrBubble() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_lava")
    public double isInLava() {
        T e = getEntity();
        return e != null && e.isInLava() ? 1.0 : 0.0;
    }

    @QueryBinding("is_on_fire")
    public double isOnFire() {
        T e = getEntity();
        return e != null && e.isOnFire() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sprinting")
    public double isSprinting() {
        T e = getEntity();
        return e != null && e.isSprinting() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sneaking")
    public double isSneaking() {
        T e = getEntity();
        return e != null && e.isCrouching() ? 1.0 : 0.0;
    }

    @QueryBinding("is_swimming")
    public double isSwimming() {
        T e = getEntity();
        return e != null && e.isSwimming() ? 1.0 : 0.0;
    }

    @QueryBinding("is_riding")
    public double isRiding() {
        T e = getEntity();
        return e != null && e.isPassenger() ? 1.0 : 0.0;
    }

    @QueryBinding("is_silent")
    public double isSilent() {
        T e = getEntity();
        return e != null && e.isSilent() ? 1.0 : 0.0;
    }

    @QueryBinding("is_no_gravity")
    public double isNoGravity() {
        T e = getEntity();
        return e != null && e.isNoGravity() ? 1.0 : 0.0;
    }

    @QueryBinding("is_invisible")
    public double isInvisible() {
        T e = getEntity();
        return e != null && e.isInvisible() ? 1.0 : 0.0;
    }

    @QueryBinding("is_glowing")
    public double isGlowing() {
        T e = getEntity();
        return e != null && e.isCurrentlyGlowing() ? 1.0 : 0.0;
    }

    // ==================== Entity 属性 ====================

    @QueryBinding("life_time")
    public double lifeTime() {
        T e = getEntity();
        return e != null ? (double) e.tickCount : 0.0;
    }

    @QueryBinding("body_y_rotation")
    public double bodyYRotation() {
        T e = getEntity();
        return e != null ? (double) e.getYRot() : 0.0;
    }

    // ==================== 位置 ====================

    @QueryBinding("position_x")
    public double positionX() {
        T e = getEntity();
        return e != null ? e.getX() : 0.0;
    }

    @QueryBinding("position_y")
    public double positionY() {
        T e = getEntity();
        return e != null ? e.getY() : 0.0;
    }

    @QueryBinding("position_z")
    public double positionZ() {
        T e = getEntity();
        return e != null ? e.getZ() : 0.0;
    }

    // ==================== Level / 时间天气 ====================

    @QueryBinding("time_of_day")
    public double timeOfDay() {
        T e = getEntity();
        return e != null ? (double) e.level().getDayTime() % 24000 : 0.0;
    }

    @QueryBinding("time_stamp")
    public double timeStamp() {
        T e = getEntity();
        return e != null ? (double) e.level().getGameTime() : 0.0;
    }

    @QueryBinding("is_day")
    public double isDay() {
        T e = getEntity();
        return e != null && e.level().isDay() ? 1.0 : 0.0;
    }

    @QueryBinding("is_night")
    public double isNight() {
        T e = getEntity();
        return e != null && e.level().isNight() ? 1.0 : 0.0;
    }

    @QueryBinding("is_raining")
    public double isRaining() {
        T e = getEntity();
        return e != null && e.level().isRaining() ? 1.0 : 0.0;
    }

    @QueryBinding("is_thundering")
    public double isThundering() {
        T e = getEntity();
        return e != null && e.level().isThundering() ? 1.0 : 0.0;
    }

    @QueryBinding("moon_phase")
    public double moonPhase() {
        T e = getEntity();
        return e != null ? (double) e.level().getMoonPhase() : 0.0;
    }
}
