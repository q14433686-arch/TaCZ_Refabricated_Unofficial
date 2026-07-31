package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 发射药类型枚举。
 * <p>
 * 对应设计文档：B.2.3 发射药类型
 */
public enum PowderType {
    /**
     * 黑火药：积碳快、烟雾大、暴露位置
     * T0-T1阶段使用，生产链简单
     */
    @SerializedName("black_powder")
    BLACK_POWDER,

    /**
     * 无烟发射药：高性能但生产链更长
     * T3+阶段使用，硝化棉基
     */
    @SerializedName("smokeless")
    SMOKELESS;

    public static final Codec<PowderType> CODEC = Codec.STRING.xmap(PowderType::valueOf, PowderType::name);
    public static final IntFunction<PowderType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, PowderType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, PowderType::ordinal);

    /**
     * 积碳速率（每发射击的积碳增量）
     */
    public float getCarbonFoulingRate() {
        return switch (this) {
            case BLACK_POWDER -> 0.5f;
            case SMOKELESS -> 0.05f;
        };
    }

    /**
     * 烟雾效果等级（0=无，1=小，2=大）
     */
    public int getSmokeLevel() {
        return switch (this) {
            case BLACK_POWDER -> 2;
            case SMOKELESS -> 0;
        };
    }

    /**
     * 声音修正系数
     */
    public float getSoundModifier() {
        return switch (this) {
            case BLACK_POWDER -> 1.2f;
            case SMOKELESS -> 1.0f;
        };
    }

    /**
     * 热量产生系数（每发射击产生的热量倍率）
     */
    public float getHeatModifier() {
        return switch (this) {
            case BLACK_POWDER -> 1.5f;
            case SMOKELESS -> 1.0f;
        };
    }

    /**
     * 所需最低科技阶段
     */
    public int getMinTechLevel() {
        return switch (this) {
            case BLACK_POWDER -> 0;  // T0
            case SMOKELESS -> 3;     // T3
        };
    }
}
