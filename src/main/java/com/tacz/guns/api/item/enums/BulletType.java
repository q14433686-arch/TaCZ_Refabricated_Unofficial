package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 弹头类型枚举。
 * <p>
 * 对应设计文档：B.2.4 弹头类型体系
 */
public enum BulletType {
    /**
     * 全被甲弹 (Full Metal Jacket)
     * 通用场景，穿透力强但停止作用差
     */
    @SerializedName("fmj")
    FMJ,

    /**
     * 空尖弹 (Hollow Point / Jacketed Hollow Point)
     * 击中目标后扩张，增加伤害但穿透力低
     */
    @SerializedName("hp")
    HP,

    /**
     * 穿甲弹 (Armor Piercing)
     * 使用硬质核心（钢/钨），穿透护甲但伤害降低
     */
    @SerializedName("ap")
    AP,

    /**
     * 曳光弹 (Tracer)
     * 弹道可见，暴露位置
     */
    @SerializedName("tracer")
    TRACER,

    /**
     * 亚音速弹 (Subsonic)
     * 配合消音器隐蔽使用，初速降低
     */
    @SerializedName("subsonic")
    SUBSONIC,

    /**
     * 燃烧弹 (Incendiary)
     * 弹头内含燃烧剂，击中目标后点燃
     * 对无甲目标伤害高，穿透力低
     */
    @SerializedName("incendiary")
    INCENDIARY;

    public static final Codec<BulletType> CODEC = Codec.STRING.xmap(BulletType::valueOf, BulletType::name);
    public static final IntFunction<BulletType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, BulletType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, BulletType::ordinal);

    /**
     * 伤害修正系数
     */
    public float getDamageModifier() {
        return switch (this) {
            case FMJ -> 1.0f;
            case HP -> 1.3f;
            case AP -> 0.8f;
            case TRACER -> 1.0f;
            case SUBSONIC -> 0.7f;
            case INCENDIARY -> 1.1f;
        };
    }

    /**
     * 穿透修正系数
     */
    public float getPierceModifier() {
        return switch (this) {
            case FMJ -> 1.0f;
            case HP -> 0.6f;
            case AP -> 1.5f;
            case TRACER -> 1.0f;
            case SUBSONIC -> 0.8f;
            case INCENDIARY -> 0.5f;
        };
    }

    /**
     * 成本修正系数
     */
    public float getCostModifier() {
        return switch (this) {
            case FMJ -> 1.0f;
            case HP -> 1.2f;
            case AP -> 1.5f;
            case TRACER -> 1.1f;
            case SUBSONIC -> 1.3f;
            case INCENDIARY -> 1.4f;
        };
    }

    /**
     * 是否为曳光弹
     */
    public boolean isTracer() {
        return this == TRACER;
    }

    /**
     * 是否为亚音速弹
     */
    public boolean isSubsonic() {
        return this == SUBSONIC;
    }

    /**
     * 是否为燃烧弹
     */
    public boolean isIncendiary() {
        return this == INCENDIARY;
    }

    /**
     * 弹头长度修正系数（相对于同口径FMJ）
     * 影响缠距匹配计算：AP弹头更长（含硬质核心），HP弹头更短
     */
    public float getBulletLengthModifier() {
        return switch (this) {
            case FMJ -> 1.0f;
            case HP -> 0.85f;
            case AP -> 1.2f;
            case TRACER -> 1.05f;
            case SUBSONIC -> 1.0f;
            case INCENDIARY -> 1.1f;
        };
    }

    /**
     * 弹头质量修正系数（相对于同口径标准弹头质量）
     */
    public float getMassModifier() {
        return switch (this) {
            case FMJ -> 1.0f;
            case HP -> 0.9f;
            case AP -> 1.15f;
            case TRACER -> 1.0f;
            case SUBSONIC -> 1.1f;
            case INCENDIARY -> 0.95f;
        };
    }

    /**
     * 所需最低科技阶段
     */
    public int getMinTechLevel() {
        return switch (this) {
            case FMJ -> 0;
            case HP -> 1;
            case AP -> 2;
            case TRACER -> 2;
            case SUBSONIC -> 2;
            case INCENDIARY -> 3;
        };
    }
}
