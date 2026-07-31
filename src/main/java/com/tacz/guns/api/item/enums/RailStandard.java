package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 导轨标准枚举。
 * <p>
 * 对应设计文档：J.2.1 导轨/挂载点兼容性系统
 */
public enum RailStandard {
    /**
     * MIL-STD-1913 皮卡汀尼导轨：大多数现代步枪通用
     */
    @SerializedName("picatinny")
    PICATINNY,

    /**
     * M-LOK：AR-15系常用
     */
    @SerializedName("mlok")
    MLOK,

    /**
     * KeyMod：部分步枪
     */
    @SerializedName("keymod")
    KEYMOD,

    /**
     * 侧导轨：AK系专用
     */
    @SerializedName("side_rail")
    SIDE_RAIL,

    /**
     * 无导轨：旧式武器，仅机械瞄具
     */
    @SerializedName("none")
    NONE;

    public static final Codec<RailStandard> CODEC = Codec.STRING.xmap(RailStandard::valueOf, RailStandard::name);
    public static final IntFunction<RailStandard> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, RailStandard> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, RailStandard::ordinal);

    /**
     * 判断两个导轨标准是否兼容
     */
    public boolean isCompatibleWith(RailStandard other) {
        if (this == other) return true;
        if (this == NONE || other == NONE) return false;
        // 不同标准默认不兼容，需要转接器
        return false;
    }
}
