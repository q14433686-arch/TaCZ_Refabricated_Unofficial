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
}
