package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 弹壳状态枚举。
 * <p>
 * 对应设计文档：B.2.5 复装系统完整流程
 * <p>
 * 弹壳状态影响复装可行性和复装后的可靠性。
 */
public enum CaseCondition {
    /**
     * 完好：首次使用，最佳状态
     */
    @SerializedName("pristine")
    PRISTINE,

    /**
     * 良好：已复装1-2次，状态良好
     */
    @SerializedName("good")
    GOOD,

    /**
     * 磨损：已复装3-4次，裂纹概率+10%
     */
    @SerializedName("worn")
    WORN,

    /**
     * 出现裂纹：不可继续复装，淘汰
     */
    @SerializedName("cracked")
    CRACKED,

    /**
     * 变形：可通过退火处理修复
     */
    @SerializedName("deformed")
    DEFORMED,

    /**
     * 锈蚀：不可复装，淘汰
     */
    @SerializedName("corroded")
    CORRODED;

    public static final Codec<CaseCondition> CODEC = Codec.STRING.xmap(CaseCondition::valueOf, CaseCondition::name);
    public static final IntFunction<CaseCondition> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, CaseCondition> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, CaseCondition::ordinal);

    /**
     * 是否可继续复装
     */
    public boolean isReloadable() {
        return this == PRISTINE || this == GOOD || this == WORN;
    }

    /**
     * 是否可通过退火修复
     */
    public boolean isRepairable() {
        return this == DEFORMED;
    }

    /**
     * 复装后的可靠性修正
     */
    public float getReliabilityModifier() {
        return switch (this) {
            case PRISTINE -> 1.0f;
            case GOOD -> 0.95f;
            case WORN -> 0.85f;
            case CRACKED, DEFORMED, CORRODED -> 0.0f;
        };
    }

    /**
     * 裂纹概率修正（每次复装额外增加的裂纹概率）
     */
    public float getCrackProbabilityBonus() {
        return switch (this) {
            case PRISTINE -> 0.0f;
            case GOOD -> 0.02f;
            case WORN -> 0.10f;
            case CRACKED, DEFORMED, CORRODED -> 1.0f; // 100%不可用
        };
    }
}
