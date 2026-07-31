package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 转轮弹巢每格状态枚举。
 * <p>
 * 转轮弹巢的每格弹膛有三种状态：空/实弹/待抛壳。
 * 与盒式弹匣的简单列表不同，转轮弹巢的每格需要独立跟踪状态。
 */
public enum ChamberState {
    /** 空格：无弹 */
    @SerializedName("empty")
    EMPTY,

    /** 实弹：已装入一发弹药 */
    @SerializedName("loaded")
    LOADED,

    /** 待抛壳：已击发，弹壳留在膛内 */
    @SerializedName("spent")
    SPENT;

    public static final Codec<ChamberState> CODEC = Codec.STRING.xmap(ChamberState::valueOf, ChamberState::name);
    public static final IntFunction<ChamberState> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, ChamberState> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ChamberState::ordinal);

    /** 是否可以装入新弹 */
    public boolean canLoad() {
        return this == EMPTY;
    }

    /** 是否可以击发 */
    public boolean canFire() {
        return this == LOADED;
    }

    /** 是否需要抛壳 */
    public boolean needsEject() {
        return this == SPENT;
    }
}
