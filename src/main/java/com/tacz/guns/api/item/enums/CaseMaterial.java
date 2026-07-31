package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 弹壳材质枚举。
 * <p>
 * 对应设计文档：B.2.1 弹壳材质差异
 */
public enum CaseMaterial {
    /**
     * 黄铜弹壳：可复装，有次数上限，无生锈风险
     */
    @SerializedName("brass")
    BRASS,

    /**
     * 钢制弹壳：廉价不可复装，暴露在雨中会生锈
     */
    @SerializedName("steel")
    STEEL,

    /**
     * 铝制弹壳：轻量不可复装
     */
    @SerializedName("aluminum")
    ALUMINUM;

    public static final Codec<CaseMaterial> CODEC = Codec.STRING.xmap(CaseMaterial::valueOf, CaseMaterial::name);
    public static final IntFunction<CaseMaterial> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, CaseMaterial> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, CaseMaterial::ordinal);

    /**
     * 最大复装次数上限
     */
    public int getMaxReloadCount() {
        return switch (this) {
            case BRASS -> 5;
            case STEEL -> 0;
            case ALUMINUM -> 0;
        };
    }

    /**
     * 是否可复装
     */
    public boolean isReloadable() {
        return getMaxReloadCount() > 0;
    }

    /**
     * 重量修正系数
     */
    public float getWeightModifier() {
        return switch (this) {
            case BRASS -> 1.0f;
            case STEEL -> 1.2f;
            case ALUMINUM -> 0.6f;
        };
    }

    /**
     * 生锈概率修正（暴露在雨中每小时）
     */
    public float getCorrosionRate() {
        return switch (this) {
            case BRASS -> 0.0f;
            case STEEL -> 0.02f;
            case ALUMINUM -> 0.0f;
        };
    }
}
