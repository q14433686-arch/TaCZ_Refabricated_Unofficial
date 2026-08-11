package com.tacz.guns.client.particle;


import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoParticle;
import com.tacz.guns.entity.EntityKineticBullet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class AmmoParticleSpawner {
    public static void addParticle(EntityKineticBullet bullet) {
        TimelessAPI.getGunDisplay(bullet.getGunDisplayId(), bullet.getGunId()).ifPresent(gunIndex -> {
            AmmoParticle gunParticle = gunIndex.getParticle();
            if (gunParticle == null) {
                // 如果枪械没有粒子效果，那么调用子弹的
                TimelessAPI.getClientAmmoIndex(bullet.getAmmoId()).ifPresent(ammoIndex -> {
                    AmmoParticle ammoParticle = ammoIndex.getParticle();
                    if (ammoParticle == null) {
                        return;
                    }
                    spawnParticle(bullet, ammoParticle);
                });
            } else {
                // 否则调用调用枪械的
                spawnParticle(bullet, gunParticle);
            }
        });
    }

    private static void spawnParticle(EntityKineticBullet bullet, AmmoParticle particle) {
        ParticleOptions particleOptions = particle.getParticleOptions();
        if (particleOptions == null) {
            return;
        }
        int count = particle.getCount();
        Vector3f delta = particle.getDelta();
        float particleSpeed = particle.getSpeed();
        ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
        // 【第 31 轮 · 尾烟（炮烟）第一人称枪口锚定】粒子沿子弹实体位置播撒，
        // 而实体出生在射者眼位 —— 不开锚定时第一人称会看到烟从自己眼睛里喷出。
        // 与弹药实体模型共用 firstPersonMuzzleAnchor 的同一条数学链：
        // 每颗粒子在播撒当刻按弹眼距取 reducer，早段落在枪口附近、随飞行
        // 自然收敛回真实弹道线；第三人称/旁观返回 null，行为与上游完全一致。
        Entity owner = bullet.getOwner();
        Vec3 anchor = owner == null ? null
                : EntityBulletRenderer.firstPersonMuzzleAnchor(bullet, bullet.position(), owner.getEyePosition());
        double ax = anchor == null ? 0 : anchor.x;
        double ay = anchor == null ? 0 : anchor.y;
        double az = anchor == null ? 0 : anchor.z;
        if (count == 0) {
            double xSpeed = particleSpeed * delta.x();
            double ySpeed = particleSpeed * delta.y();
            double zSpeed = particleSpeed * delta.z();
            Particle result = particleEngine.createParticle(particleOptions, bullet.getX() + ax, bullet.getY() + ay, bullet.getZ() + az, xSpeed, ySpeed, zSpeed);
            if (result != null) {
                result.setLifetime(particle.getLifeTime());
            }
        } else {
            RandomSource random = bullet.getRandom();
            for (int i = 0; i < count; ++i) {
                createParticle(bullet, particle, random, delta, particleSpeed, owner, particleEngine, particleOptions, anchor);
            }
        }
    }

    private static void createParticle(EntityKineticBullet bullet, AmmoParticle particle, RandomSource random, Vector3f delta, float particleSpeed, Entity owner, ParticleEngine particleEngine, ParticleOptions particleOptions, Vec3 anchor) {
        Vec3 deltaMovement = bullet.getDeltaMovement();
        double deltaMovementRandom = random.nextDouble();
        double offsetX = random.nextGaussian() * delta.x() + deltaMovementRandom * deltaMovement.x;
        double offsetY = random.nextGaussian() * delta.y() + deltaMovementRandom * deltaMovement.y;
        double offsetZ = random.nextGaussian() * delta.z() + deltaMovementRandom * deltaMovement.z;
        double xSpeed = random.nextGaussian() * particleSpeed;
        double ySpeed = random.nextGaussian() * particleSpeed;
        double zSpeed = random.nextGaussian() * particleSpeed;

        double posX = bullet.getX() + offsetX;
        double posY = bullet.getY() + offsetY;
        double posZ = bullet.getZ() + offsetZ;
        if (anchor != null) {
            posX += anchor.x;
            posY += anchor.y;
            posZ += anchor.z;
        }

        // 如果太贴近发射者，不进行粒子生成
        if (owner == null || owner.distanceToSqr(posX, posY, posZ) > 3 * 3) {
            Particle result = particleEngine.createParticle(particleOptions, posX, posY, posZ, xSpeed, ySpeed, zSpeed);
            if (result != null) {
                result.setLifetime(particle.getLifeTime());
            }
        }
    }
}
