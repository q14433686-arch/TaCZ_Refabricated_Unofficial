package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 弹链链节类型枚举。
 * <p>
 * 弹链的链节类型决定了弹链的物理特性和使用方式。
 */
public enum BeltLinkType {
    /** 可散式弹链：弹壳抽出后链节自动脱落，轻量但不可重复使用 */
    @SerializedName("disintegrating")
    DISINTEGRATING,

    /** 不可散式弹链：链节不脱落，可重复装填，但较重 */
    @SerializedName("non_disintegrating")
    NON_DISINTEGRATING;

    public static final Codec<BeltLinkType> CODEC = Codec.STRING.xmap(BeltLinkType::valueOf, BeltLinkType::name);
    public static final IntFunction<BeltLinkType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, BeltLinkType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, BeltLinkType::ordinal);

    /** 是否可重复使用 */
    public boolean isReusable() {
        return this == NON_DISINTEGRATING;
    }

    /** 重量修正系数 */
    public float getWeightModifier() {
        return switch (this) {
            case DISINTEGRATING -> 1.0f;
            case NON_DISINTEGRATING -> 1.5f;
        };
    }
}
