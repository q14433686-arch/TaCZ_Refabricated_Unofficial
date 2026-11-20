package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Particle-specific query bindings; model-animation query adapters are not reused. */
public final class ParticleEvaluationContext extends MolangContext<Object> {
    float dt=.05f;
    @QueryBinding("delta_time") public double deltaTime(){return dt;}
    @QueryBinding("life_time") public double lifetime(){return getTimeS();}
    @QueryBinding("is_alive") public double alive(){return getEntity() instanceof Entity e&&e.isAlive()?1:0;}
    @QueryBinding("is_on_ground") public double ground(){return getEntity() instanceof Entity e&&e.onGround()?1:0;}
    @QueryBinding("is_sprinting") public double sprint(){return getEntity() instanceof Entity e&&e.isSprinting()?1:0;}
    @QueryBinding("is_sneaking") public double sneak(){return getEntity() instanceof Entity e&&e.isShiftKeyDown()?1:0;}
    @QueryBinding("health") public double health(){return getEntity() instanceof LivingEntity e?e.getHealth():0;}
    @QueryBinding("max_health") public double maxHealth(){return getEntity() instanceof LivingEntity e?e.getMaxHealth():0;}
    @QueryBinding("ground_speed") public double speed(){return getEntity() instanceof Entity e?e.getDeltaMovement().horizontalDistance()*20:0;}
    @QueryBinding("vertical_speed") public double vertical(){return getEntity() instanceof Entity e?e.getDeltaMovement().y*20:0;}
}
