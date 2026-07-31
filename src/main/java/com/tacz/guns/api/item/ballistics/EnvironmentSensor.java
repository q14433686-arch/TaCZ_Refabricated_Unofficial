package com.tacz.guns.api.item.ballistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * 环境检测器。
 * <p>
 * 检测射击者所在生物群系的环境条件，影响：
 * <ul>
 *   <li>冷却速率：水中/雨中加速冷却，沙漠减慢冷却</li>
 *   <li>锈蚀速率：雨中/水中加速锈蚀</li>
 *   <li>卡壳概率：沙尘环境增加卡壳率</li>
 *   <li>循环速度：严寒环境润滑油凝滞，循环速度降低</li>
 *   <li>枪管异物：水中/沙尘环境可能进入异物</li>
 * </ul>
 * <p>
 * 对应设计文档：H.2.4 环境交互
 * <p>
 * 检测频率：每20tick检测一次（避免每tick检测的开销）
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class EnvironmentSensor {

    /** 沙漠群系温度阈值（Minecraft生物群系温度 > 此值视为沙漠） */
    public static final float DESERT_TEMPERATURE_THRESHOLD = 1.0f;

    /** 严寒群系温度阈值（Minecraft生物群系温度 < 此值视为严寒） */
    public static final float COLD_TEMPERATURE_THRESHOLD = 0.15f;

    /** 环境检测间隔（tick） */
    public static final int CHECK_INTERVAL = 20;

    private EnvironmentSensor() {}

    /**
     * 检测当前环境条件。
     *
     * @param shooter 射击者
     * @return 环境条件快照
     */
    public static EnvironmentCondition detect(@Nullable LivingEntity shooter) {
        if (shooter == null) {
            return EnvironmentCondition.DEFAULT;
        }

        Level level = shooter.level();
        BlockPos pos = shooter.blockPosition();

        // 检测是否在水中
        boolean isInWater = shooter.isInWater();

        // 检测是否在雨中
        boolean isRaining = level.isRaining() && level.canSeeSky(pos);

        // 检测生物群系温度
        Biome biome = level.getBiome(pos).value();
        float biomeTemperature = biome.getBaseTemperature();

        boolean isDesert = biomeTemperature > DESERT_TEMPERATURE_THRESHOLD;
        boolean isCold = biomeTemperature < COLD_TEMPERATURE_THRESHOLD;

        // 检测是否在雷暴中
        boolean isThundering = level.isThundering() && level.canSeeSky(pos);

        return new EnvironmentCondition(isInWater, isRaining, isDesert, isCold, isThundering);
    }

    /**
     * 获取环境对冷却速率的修正系数。
     *
     * @param condition 环境条件
     * @return 冷却修正系数（>1.0=加速冷却，<1.0=减慢冷却）
     */
    public static float getCoolingModifier(EnvironmentCondition condition) {
        return OverheatExpansion.getEnvironmentCoolingModifier(
                condition.isInWater, condition.isRaining, condition.isDesert);
    }

    /**
     * 获取环境对锈蚀速率的修正系数。
     *
     * @param condition 环境条件
     * @return 锈蚀修正系数（1.0=正常）
     */
    public static float getCorrosionModifier(EnvironmentCondition condition) {
        if (condition.isInWater) return 25.0f;
        if (condition.isRaining) return 5.0f;
        return 1.0f;
    }

    /**
     * 获取环境对卡壳概率的额外贡献。
     *
     * @param condition 环境条件
     * @return 卡壳概率额外贡献（0.0~0.01）
     */
    public static float getMalfunctionBonus(EnvironmentCondition condition) {
        float bonus = 0.0f;
        if (condition.isDesert) bonus += 0.01f;  // 沙尘：+1.0%/发
        if (condition.isCold) bonus += 0.005f;    // 严寒：+0.5%/发
        return bonus;
    }

    /**
     * 获取环境对循环速度的修正系数。
     *
     * @param condition 环境条件
     * @return 循环速度修正系数（1.0=正常，<1.0=减慢）
     */
    public static float getCycleSpeedModifier(EnvironmentCondition condition) {
        if (condition.isCold) return 0.80f;  // 严寒：润滑油凝滞，-20%
        return 1.0f;
    }

    /**
     * 判断是否应该检测枪管异物（沙尘/水进入枪管）。
     * <p>
     * 概率模型：在恶劣环境中每60秒检测一次，概率较低。
     *
     * @param condition 环境条件
     * @param random    随机数（0.0~1.0）
     * @return 是否应该设置枪管异物
     */
    public static boolean shouldContaminateBarrel(EnvironmentCondition condition, double random) {
        if (condition.isInWater && random < 0.005) return true;   // 水中：0.5%/次
        if (condition.isDesert && random < 0.002) return true;    // 沙尘：0.2%/次
        if (condition.isRaining && random < 0.001) return true;   // 雨中：0.1%/次
        return false;
    }

    /**
     * 环境条件快照（不可变记录）。
     */
    public record EnvironmentCondition(
            /** 是否在水中 */
            boolean isInWater,
            /** 是否在雨中 */
            boolean isRaining,
            /** 是否在沙漠 */
            boolean isDesert,
            /** 是否在严寒 */
            boolean isCold,
            /** 是否在雷暴中 */
            boolean isThundering
    ) {
        /** 默认环境条件（正常环境） */
        public static final EnvironmentCondition DEFAULT = new EnvironmentCondition(false, false, false, false, false);
    }
}
