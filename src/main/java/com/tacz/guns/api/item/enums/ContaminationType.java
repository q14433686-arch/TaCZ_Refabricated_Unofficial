package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 枪管异物/污染类型枚举。
 * <p>
 * 对应设计文档：F.2.1 炸膛触发条件
 */
public enum ContaminationType {
    @SerializedName("none")
    NONE,

    @SerializedName("mud")
    MUD,

    @SerializedName("sand")
    SAND,

    @SerializedName("water")
    WATER;

    public static final Codec<ContaminationType> CODEC = Codec.STRING.xmap(ContaminationType::valueOf, ContaminationType::name);
    public static final IntFunction<ContaminationType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, ContaminationType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ContaminationType::ordinal);

    /**
     * 炸膛风险修正系数
     */
    public float getCatastrophicRiskModifier() {
        return switch (this) {
            case NONE -> 0.0f;
            case MUD -> 0.5f;
            case SAND -> 0.3f;
            case WATER -> 1.0f; // 水不可压缩，膛压骤增，风险最高
        };
    }

    /**
     * 是否有污染
     */
    public boolean isContaminated() {
        return this != NONE;
    }
}
