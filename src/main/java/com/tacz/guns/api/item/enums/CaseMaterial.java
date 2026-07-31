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
    ALUMINUM,

    /**
     * 聚合物弹壳：最轻量不可复装，耐腐蚀
     * T3+阶段使用，现代弹药（如PCP聚合物弹壳）
     */
    @SerializedName("polymer")
    POLYMER;

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
            case POLYMER -> 0;
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
            case POLYMER -> 0.5f;
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
            case POLYMER -> 0.0f;
        };
    }

    /**
     * 弹壳强度系数（影响膛压承受能力和抽壳可靠性）
     */
    public float getStrengthModifier() {
        return switch (this) {
            case BRASS -> 1.0f;
            case STEEL -> 1.3f;
            case ALUMINUM -> 0.7f;
            case POLYMER -> 0.6f;
        };
    }

    /**
     * 抽壳摩擦系数（影响抽壳可靠性）
     * 黄铜弹性好，抽壳顺畅；钢/铝摩擦大
     */
    public float getExtractionFriction() {
        return switch (this) {
            case BRASS -> 1.0f;
            case STEEL -> 1.4f;
            case ALUMINUM -> 1.2f;
            case POLYMER -> 0.8f;
        };
    }

    /**
     * 所需最低科技阶段
     */
    public int getMinTechLevel() {
        return switch (this) {
            case BRASS -> 1;   // T1+
            case STEEL -> 1;   // T1+
            case ALUMINUM -> 3; // T3+
            case POLYMER -> 3;  // T3+
        };
    }
}
