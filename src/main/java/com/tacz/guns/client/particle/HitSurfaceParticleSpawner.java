package com.tacz.guns.client.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.init.ModParticles;
import net.minecraft.client.particle.SingleQuadParticle;
import com.tacz.guns.client.resource.pojo.display.ammo.HitSurfaceParticle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.ColorHex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

public final class HitSurfaceParticleSpawner {
    private static final int MAX_BLOCK_PARTICLE_COUNT = 32;
    private static final int MAX_DISPLAY_PARTICLE_COUNT_PER_HIT = 64;
    private static final float DEFAULT_SURFACE_OFFSET = 0.03f;
    private static final float DEFAULT_RANDOM_OFFSET = 0.01f;
    private static final int DEFAULT_BLOCK_PARTICLE_COUNT = 3;
    private static final float DEFAULT_BLOCK_NORMAL_SPEED = 0.14f;
    private static final float DEFAULT_BLOCK_RANDOM_SPEED = 0.015f;
    private static final float DEFAULT_BLOCK_SPREAD_ANGLE = 30.0f;
    private static final String DUST = "minecraft:dust";
    private static final String DUST_COLOR_TRANSITION = "minecraft:dust_color_transition";
    private static final String BLOCK = "minecraft:block";
    private static final String FALLING_DUST = "minecraft:falling_dust";
    private static final String ITEM = "minecraft:item";

    private HitSurfaceParticleSpawner() {
    }

    /** Called from the existing impact particle provider; never generate a second network event. */
    static void spawn(ClientLevel level, Vec3 hitPos, Direction face, BlockState state,
                      @Nullable ClientAmmoIndex ammo, @Nullable GunDisplayInstance gun, boolean incendiary) {
        if (!RenderConfig.HIT_PARTICLE_ENABLE.get() || !isCloseEnough(hitPos)) return;
        var engine = Minecraft.getInstance().particleEngine;
        var random = level.getRandom();
        Vec3 normal = face.getUnitVec3();
        spawnBlockParticles(engine, random, state, hitPos, normal, incendiary, ammo == null ? null : ammo.getHitBlockParticleCount());
        if (!RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.get()) return;
        var particles = gun == null ? null : gun.getHitSurfaceParticles();
        if (particles == null && ammo != null) particles = ammo.getHitSurfaceParticles();
        if (particles == null) return; // Empty gun-level list intentionally suppresses the ammo default.
        int remaining = MAX_DISPLAY_PARTICLE_COUNT_PER_HIT;
        for (int i = 0; i < Math.min(particles.size(), MAX_DISPLAY_PARTICLE_COUNT_PER_HIT) && remaining > 0; i++) {
            var effect = particles.get(i);
            if (effect != null) remaining -= spawnDisplayParticle(engine, random, state, hitPos, normal, effect, remaining, level);
        }
    }

    private static boolean isCloseEnough(Vec3 hitPos) {
        int maxDistance = RenderConfig.HIT_PARTICLE_MAX_DISTANCE.get();
        if (maxDistance <= 0) {
            return true;
        }
        Player player = Minecraft.getInstance().player;
        double maxDistanceSqr = (double) maxDistance * (double) maxDistance;
        return player != null && player.distanceToSqr(hitPos) <= maxDistanceSqr;
    }

    private static void spawnBlockParticles(ParticleEngine particleEngine, RandomSource random, BlockState blockState, Vec3 hitPos, Vec3 normal, boolean incendiary, @Nullable Integer configuredCount) {
        int count = configuredCount == null ? DEFAULT_BLOCK_PARTICLE_COUNT : Math.min(configuredCount, MAX_BLOCK_PARTICLE_COUNT);
        if (count <= 0) {
            return;
        }
        ParticleOptions options = incendiary ? ParticleTypes.LAVA : new BlockParticleOption(ParticleTypes.BLOCK, blockState);
        for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(random, hitPos, normal, DEFAULT_SURFACE_OFFSET, DEFAULT_RANDOM_OFFSET);
            Vec3 speed = randomVelocity(random,
                    normal,
                    DEFAULT_BLOCK_NORMAL_SPEED,
                    DEFAULT_BLOCK_RANDOM_SPEED,
                    DEFAULT_BLOCK_SPREAD_ANGLE);
            particleEngine.createParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
        }
    }

    private static int spawnDisplayParticle(ParticleEngine particleEngine, RandomSource random, BlockState blockState, Vec3 hitPos, Vec3 normal, HitSurfaceParticle particle, int maxCount, ClientLevel level) {
        ParticleOptions options = parseParticle(level, blockState, particle);
        if (options == null || options.getType() == ModParticles.BULLET_HOLE || maxCount <= 0) {
            return 0;
        }
        int count = Math.min(randomCount(random, particle), maxCount);
        for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(random, hitPos, normal, particle.getSurfaceOffset(), particle.getRandomOffset());
            Vec3 speed = randomVelocity(random, normal, particle.getNormalSpeed(), particle.getRandomSpeed(), particle.getSpreadAngle());
            Particle result = particleEngine.createParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
            if (result != null) {
                applyParticleOverrides(result, particle);
            }
        }
        return count; // Invalid/rejected provider output still spends the per-hit attempt budget.
    }

    private static int randomCount(RandomSource random, HitSurfaceParticle particle) {
        int min = particle.getCountMin();
        int max = particle.getCountMax();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (max == min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    @Nullable
    private static ParticleOptions parseParticle(ClientLevel level, BlockState blockState, HitSurfaceParticle particle) {
        String name = particle.getName();
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String trimmedName = name.trim();
        boolean hasArguments = trimmedName.indexOf('{') >= 0 || trimmedName.indexOf(' ') >= 0;
        if (hasArguments) {
            try {
                return ParticleArgument.readParticle(new StringReader(trimmedName), level.registryAccess());
            } catch (CommandSyntaxException ignored) {
                return null;
            }
        }
        String particleId = normalizeParticleId(trimmedName);
        if (BLOCK.equals(particleId)) {
            return new BlockParticleOption(ParticleTypes.BLOCK, blockState);
        }
        if (FALLING_DUST.equals(particleId)) {
            return new BlockParticleOption(ParticleTypes.FALLING_DUST, blockState);
        }
        if (DUST.equals(particleId)) {
            return new DustParticleOptions(dustColor(particle), positiveScale(particle));
        }
        if (DUST_COLOR_TRANSITION.equals(particleId)) {
            int from = dustColor(particle);
            return new DustColorTransitionOptions(from, (from & 0xFCFCFC) >>> 2, positiveScale(particle));
        }
        if (ITEM.equals(particleId)) {
            return null;
        }
        try {
            return ParticleArgument.readParticle(new StringReader(trimmedName), level.registryAccess());
        } catch (CommandSyntaxException ignored) {
            return null;
        }
    }

    private static String normalizeParticleId(String particleName) {
        String firstToken = particleName.split("\\s+", 2)[0];
        Identifier id = Identifier.tryParse(firstToken);
        if (id == null) {
            return firstToken;
        }
        return id.toString();
    }

    private static float positiveScale(HitSurfaceParticle particle) {
        Float scale = particle.getScale();
        return scale == null ? 1.0f : Math.max(scale, 0.0f);
    }

    private static int dustColor(HitSurfaceParticle particle) {
        return StringUtils.isBlank(particle.getColor()) ? 0xff591a : ColorHex.colorTextToRbgInt(particle.getColor());
    }

    private static void applyParticleOverrides(Particle result, HitSurfaceParticle particle) {
        result.setLifetime(particle.getLifeTime());
        String color = particle.getColor();
        if (result instanceof SingleQuadParticle quad) {
            if (particle.getAlpha() != null) quad.setAlpha(particle.getAlpha());
        }
        if (result instanceof SingleQuadParticle quad && StringUtils.isNotBlank(color)) {
            float[] rgb = ColorHex.colorTextToRbgFloatArray(color);
            quad.setColor(rgb[0], rgb[1], rgb[2]);
        }
        Float scale = particle.getScale();
        if (scale != null && scale > 0 && !usesScaleInParticleOptions(particle)) {
            result.scale(scale);
        }
    }

    private static boolean usesScaleInParticleOptions(HitSurfaceParticle particle) {
        String name = particle.getName();
        if (StringUtils.isBlank(name)) {
            return false;
        }
        String particleId = normalizeParticleId(name.trim());
        return DUST.equals(particleId) || DUST_COLOR_TRANSITION.equals(particleId);
    }

    private static Vec3 randomPosition(RandomSource random, Vec3 hitPos, Vec3 normal, float surfaceOffset, float randomOffset) {
        double offsetX = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        double offsetY = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        double offsetZ = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        return hitPos.add(normal.scale(surfaceOffset)).add(offsetX, offsetY, offsetZ);
    }

    private static Vec3 randomVelocity(RandomSource random, Vec3 normal, float normalSpeed, float randomSpeed, float spreadAngle) {
        normal = normal.normalize();
        Vec3 tangent = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : normal.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();
        double spread = Math.tan(Math.toRadians(spreadAngle));
        Vec3 cone = normal
                .add(tangent.scale(random.nextGaussian() * spread))
                .add(bitangent.scale(random.nextGaussian() * spread))
                .normalize()
                .scale(normalSpeed);
        Vec3 randomVector = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(randomSpeed);
        return cone.add(randomVector);
    }
}
