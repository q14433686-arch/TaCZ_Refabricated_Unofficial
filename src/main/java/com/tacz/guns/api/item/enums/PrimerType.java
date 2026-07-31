package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 底火类型枚举。
 * <p>
 * 对应设计文档：B.2.2 底火类型
 * <p>
 * Boxer式底火弹壳只有一个底火孔，复装时可用工具顶出废底火，因此可复装；
 * Berdan式底火弹壳有两个底火孔，复装时无法取出废底火，因此通常不可复装。
 */
public enum PrimerType {
    /**
     * Boxer式底火：中心一个底火砧，弹壳只有一个底火孔
     * 可复装体系中使用
     */
    @SerializedName("boxer")
    BOXER,

    /**
     * Berdan式底火：两个偏心底火孔，底火砧在弹壳内部
     * 不可复装体系中使用（军事弹药普遍采用）
     */
    @SerializedName("berdan")
    BERDAN;

    public static final Codec<PrimerType> CODEC = Codec.STRING.xmap(PrimerType::valueOf, PrimerType::name);
    public static final IntFunction<PrimerType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, PrimerType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, PrimerType::ordinal);

    /**
     * 是否支持复装（Boxer式支持，Berdan式不支持）
     */
    public boolean isReloadable() {
        return this == BOXER;
    }
}
