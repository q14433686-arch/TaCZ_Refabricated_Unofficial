package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.ContaminationType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 枪械保养状态数据组件。
 * <p>
 * 对应设计文档：H.4.1 保养状态数据
 * <p>
 * 存储枪械的积碳、锈蚀、润滑、枪管异物等状态。
 */
public record GunMaintenanceData(
        float carbonFoulingLevel,
        float corrosionLevel,
        float lubricationLevel,
        ContaminationType contaminationType,
        long lastMaintenanceTimestamp
) {
    /**
     * 创建默认的保养数据（无积碳、无锈蚀、满润滑、无异物）
     */
    public static GunMaintenanceData createDefault() {
        return new GunMaintenanceData(0.0f, 0.0f, 100.0f, ContaminationType.NONE, 0L);
    }

    public static final Codec<GunMaintenanceData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("carbon_fouling").forGetter(GunMaintenanceData::carbonFoulingLevel),
                    Codec.FLOAT.fieldOf("corrosion").forGetter(GunMaintenanceData::corrosionLevel),
                    Codec.FLOAT.fieldOf("lubrication").forGetter(GunMaintenanceData::lubricationLevel),
                    ContaminationType.CODEC.fieldOf("contamination").forGetter(GunMaintenanceData::contaminationType),
                    Codec.LONG.fieldOf("last_maintenance").forGetter(GunMaintenanceData::lastMaintenanceTimestamp)
            ).apply(instance, GunMaintenanceData::new)
    );

    public static final StreamCodec<ByteBuf, GunMaintenanceData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, GunMaintenanceData::carbonFoulingLevel,
            ByteBufCodecs.FLOAT, GunMaintenanceData::corrosionLevel,
            ByteBufCodecs.FLOAT, GunMaintenanceData::lubricationLevel,
            ContaminationType.STREAM_CODEC, GunMaintenanceData::contaminationType,
            ByteBufCodecs.LONG, GunMaintenanceData::lastMaintenanceTimestamp,
            GunMaintenanceData::new
    );

    /**
     * 积碳对卡壳概率的额外贡献
     */
    public float getCarbonMalfunctionBonus() {
        if (carbonFoulingLevel > 80) return 0.02f;
        if (carbonFoulingLevel > 60) return 0.005f;
        if (carbonFoulingLevel > 30) return 0.001f;
        return 0.0f;
    }

    /**
     * 锈蚀对卡壳概率的额外贡献
     */
    public float getCorrosionMalfunctionBonus() {
        if (corrosionLevel > 75) return 0.05f;
        if (corrosionLevel > 50) return 0.02f;
        if (corrosionLevel > 25) return 0.005f;
        return 0.0f;
    }

    /**
     * 润滑对循环速度的修正
     */
    public float getCycleSpeedModifier() {
        if (lubricationLevel > 50) return 1.0f;
        if (lubricationLevel > 20) return 0.95f;
        return 0.80f; // 干涩状态
    }

    /**
     * 添加积碳
     */
    public GunMaintenanceData withCarbonAdded(float amount) {
        return new GunMaintenanceData(
                Math.min(100.0f, carbonFoulingLevel + amount),
                corrosionLevel, lubricationLevel, contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * 添加锈蚀
     */
    public GunMaintenanceData withCorrosionAdded(float amount) {
        return new GunMaintenanceData(
                carbonFoulingLevel, Math.min(100.0f, corrosionLevel + amount),
                lubricationLevel, contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * 润滑衰减
     */
    public GunMaintenanceData withLubricationDecay(float amount) {
        return new GunMaintenanceData(
                carbonFoulingLevel, corrosionLevel,
                Math.max(0.0f, lubricationLevel - amount),
                contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * 清洁（减少积碳）
     */
    public GunMaintenanceData withCleaning(float carbonReduction) {
        return new GunMaintenanceData(
                Math.max(0.0f, carbonFoulingLevel - carbonReduction),
                corrosionLevel, lubricationLevel, contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * 润滑
     */
    public GunMaintenanceData withLubrication(float amount) {
        return new GunMaintenanceData(
                carbonFoulingLevel, corrosionLevel,
                Math.min(100.0f, lubricationLevel + amount),
                contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * 设置枪管异物
     */
    public GunMaintenanceData withContamination(ContaminationType type) {
        return new GunMaintenanceData(
                carbonFoulingLevel, corrosionLevel, lubricationLevel, type, lastMaintenanceTimestamp
        );
    }

    // ====== P3过热/炸膛/保养扩展方法 ======

    /**
     * P3扩展：射击后积碳累积。
     * <p>
     * 使用 PowderType.getCarbonFoulingRate() 作为 carbonRate。
     * <ul>
     *   <li>黑火药：0.5/发（10发后需清洁）</li>
     *   <li>无烟火药：0.05/发（100发后需清洁）</li>
     *   <li>双基药：0.04/发</li>
     *   <li>三基药：0.02/发</li>
     * </ul>
     *
     * @param carbonRate 积碳速率（来自 PowderType.getCarbonFoulingRate()）
     * @return 更新后的 GunMaintenanceData
     */
    public GunMaintenanceData withCarbonFromShot(float carbonRate) {
        return new GunMaintenanceData(
                Math.min(100.0f, carbonFoulingLevel + carbonRate),
                corrosionLevel, lubricationLevel, contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * P3扩展：每tick润滑衰减。
     * <p>
     * 润滑油在枪械使用过程中逐渐消耗，衰减速率约 -0.001/tick。
     * 干涩状态（lubrication < 20）会导致循环速度降低20%。
     *
     * @return 更新后的 GunMaintenanceData
     */
    public GunMaintenanceData withLubricationTickDecay() {
        return new GunMaintenanceData(
                carbonFoulingLevel, corrosionLevel,
                Math.max(0.0f, lubricationLevel - 0.001f),
                contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * P3扩展：环境锈蚀累积。
     * <p>
     * 锈蚀速率取决于环境条件和润滑状态：
     * <ul>
     *   <li>正常环境：0.00002/tick（约0.0004/秒）</li>
     *   <li>暴露雨中：0.0002/tick（约0.004/秒）</li>
     *   <li>水中：0.001/tick（约0.02/秒）</li>
     *   <li>涂油状态：×0.1（大幅减缓）</li>
     *   <li>钢制弹壳使用后：×2.0（额外加速）</li>
     * </ul>
     *
     * @param isRaining  是否在雨中
     * @param isInWater  是否在水中
     * @param isOiled    是否有润滑（lubricationLevel > 30）
     * @param isCorrosiveAmmoUsed 是否使用过腐蚀性弹药
     * @return 更新后的 GunMaintenanceData
     */
    public GunMaintenanceData withCorrosionFromEnvironment(boolean isRaining, boolean isInWater,
                                                           boolean isOiled, boolean isCorrosiveAmmoUsed) {
        float rate = getCorrosionRate(isRaining, isInWater, isOiled, isCorrosiveAmmoUsed);
        return new GunMaintenanceData(
                carbonFoulingLevel, Math.min(100.0f, corrosionLevel + rate),
                lubricationLevel, contaminationType, lastMaintenanceTimestamp
        );
    }

    /**
     * P3扩展：计算当前环境下的锈蚀速率（每tick）。
     *
     * @param isRaining  是否在雨中
     * @param isInWater  是否在水中
     * @param isOiled    是否有润滑
     * @param isCorrosiveAmmoUsed 是否使用过腐蚀性弹药
     * @return 锈蚀速率（每tick）
     */
    public float getCorrosionRate(boolean isRaining, boolean isInWater, boolean isOiled, boolean isCorrosiveAmmoUsed) {
        float baseRate;
        if (isInWater) {
            baseRate = 0.001f;
        } else if (isRaining) {
            baseRate = 0.0002f;
        } else {
            baseRate = 0.00002f;
        }

        // 润滑减缓
        if (isOiled) {
            baseRate *= 0.1f;
        }

        // 腐蚀性弹药加速
        if (isCorrosiveAmmoUsed) {
            baseRate *= 2.0f;
        }

        return baseRate;
    }

    /**
     * P3扩展：积碳+锈蚀的精度惩罚。
     * <p>
     * 综合积碳和锈蚀对精度的负面影响：
     * <ul>
     *   <li>积碳 > 60: -5%</li>
     *   <li>积碳 > 80: -15%</li>
     *   <li>锈蚀 > 25: -5%</li>
     *   <li>锈蚀 > 50: -15%</li>
     *   <li>锈蚀 > 75: -30%</li>
     * </ul>
     *
     * @return 精度修正系数（1.0=无惩罚，<1.0=精度下降）
     */
    public float getAccuracyPenalty() {
        float penalty = 1.0f;

        // 积碳影响
        if (carbonFoulingLevel > 80) penalty *= 0.85f;
        else if (carbonFoulingLevel > 60) penalty *= 0.95f;

        // 锈蚀影响
        if (corrosionLevel > 75) penalty *= 0.70f;
        else if (corrosionLevel > 50) penalty *= 0.85f;
        else if (corrosionLevel > 25) penalty *= 0.95f;

        return penalty;
    }

    /**
     * P3扩展：积碳+锈蚀的卡壳概率额外贡献。
     * <p>
     * 综合积碳和锈蚀对卡壳概率的影响：
     * <ul>
     *   <li>积碳 > 30: +0.1%</li>
     *   <li>积碳 > 60: +0.5%</li>
     *   <li>积碳 > 80: +2.0%</li>
     *   <li>锈蚀 > 25: +0.5%</li>
     *   <li>锈蚀 > 50: +2.0%</li>
     *   <li>锈蚀 > 75: +5.0%</li>
     * </ul>
     *
     * @return 卡壳概率额外贡献（0.0~0.07）
     */
    public float getMalfunctionBonus() {
        float bonus = 0.0f;

        // 积碳影响
        bonus += getCarbonMalfunctionBonus();

        // 锈蚀影响
        bonus += getCorrosionMalfunctionBonus();

        return bonus;
    }
}
