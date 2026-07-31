package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 供弹具类型枚举。
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化系统
 * <p>
 * 不同类型的供弹具在结构、容量、换弹方式、可靠性方面有根本性差异，
 * 不能全部套用同一套"弹匣"代码。
 */
public enum FeedSystemType {
    /**
     * 转轮弹巢：固定在枪上，不可拆卸
     * 容量5-8发，逐个装弹或快速装弹器
     * 可靠性极高
     */
    @SerializedName("cylinder")
    CYLINDER,

    /**
     * 管状弹仓：固定在枪管下方
     * 容量4-8发，逐发从装弹口塞入
     * 可靠性高（泵动霰弹枪/管状弹仓步枪）
     */
    @SerializedName("tubular_magazine")
    TUBULAR_MAGAZINE,

    /**
     * 桥夹固定弹仓：固定在枪内，用桥夹压入
     * 容量5-10发，桥 clip压入→弹仓→逐发供弹
     * 可靠性高（如Kar98k、M1 Garand）
     */
    @SerializedName("stripper_clip")
    STRIPPER_CLIP,

    /**
     * 可拆卸弹匣：可从枪上取下
     * 容量10-30发，拔出旧弹匣→插入新弹匣
     * 可靠性中等（大多数现代步枪/手枪）
     */
    @SerializedName("detachable_magazine")
    DETACHABLE_MAGAZINE,

    /**
     * 漏夹：弹夹整体插入弹仓
     * 容量5-8发，整体插入→弹仓→空夹弹出
     * 可靠性中等（如M1 Garand的en bloc clip）
     */
    @SerializedName("en_bloc")
    EN_BLOC,

    /**
     * 弹链：金属/布弹链
     * 容量50-200发，更换弹链箱
     * 可靠性低（需润滑，机枪类武器）
     */
    @SerializedName("belt")
    BELT,

    /**
     * 弹鼓：大容量可拆卸
     * 容量50-100发，笨重
     * 可靠性低（弹簧疲劳快）
     */
    @SerializedName("drum")
    DRUM;

    public static final Codec<FeedSystemType> CODEC = Codec.STRING.xmap(FeedSystemType::valueOf, FeedSystemType::name);
    public static final IntFunction<FeedSystemType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, FeedSystemType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, FeedSystemType::ordinal);

    /**
     * 是否为可拆卸供弹具
     */
    public boolean isDetachable() {
        return this == DETACHABLE_MAGAZINE || this == DRUM;
    }

    /**
     * 基础换弹时间倍率（相对于可拆卸弹匣的1.0）
     */
    public float getReloadTimeMultiplier() {
        return switch (this) {
            case CYLINDER -> 1.8f;          // 逐发装填较慢
            case TUBULAR_MAGAZINE -> 2.0f;  // 逐发塞入
            case STRIPPER_CLIP -> 0.8f;      // 桥 clip压入较快
            case DETACHABLE_MAGAZINE -> 1.0f; // 基准
            case EN_BLOC -> 0.9f;            // 整体插入
            case BELT -> 2.5f;               // 更换弹链箱
            case DRUM -> 1.5f;               // 笨重
        };
    }

    /**
     * 基础故障概率修正
     */
    public float getMalfunctionModifier() {
        return switch (this) {
            case CYLINDER -> 0.5f;           // 极高可靠
            case TUBULAR_MAGAZINE -> 0.7f;   // 高可靠
            case STRIPPER_CLIP -> 0.7f;       // 高可靠
            case DETACHABLE_MAGAZINE -> 1.0f; // 基准
            case EN_BLOC -> 1.0f;             // 中等
            case BELT -> 1.5f;               // 低可靠
            case DRUM -> 1.5f;               // 低可靠
        };
    }
}
