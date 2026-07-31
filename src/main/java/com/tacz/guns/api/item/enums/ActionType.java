package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 枪械自动原理类型枚举。
 * <p>
 * 扩展自 TACZ 原有的 {@code Bolt} 枚举，
 * 将原来简单的"开膛/闭膛/手动"三分类细化为7种自动原理，
 * 每种原理有不同的后坐力曲线特征和循环可靠性参数。
 * <p>
 * 对应设计文档：D.2.1 后坐力类型与自动原理挂钩
 */
public enum ActionType {
    /**
     * 直吹式：无闭锁机构，枪机质量+复进簧力抵抗膛压
     * 后坐力来得早且猛，典型如 MAC-10、UZI
     */
    @SerializedName("blowback")
    BLOWBACK,

    /**
     * 延迟后吹式：通过滚轮/杠杆等机构延迟开锁
     * 后坐力曲线较平缓，典型如 MP5、G3
     */
    @SerializedName("delayed_blowback")
    DELAYED_BLOWBACK,

    /**
     * 管退式：枪管与枪机共同后坐一段距离后开锁
     * 后坐力分布均匀，典型如 M1911、M2 重机枪
     */
    @SerializedName("recoil_operated")
    RECOIL_OPERATED,

    /**
     * 长行程导气式：活塞与枪机框一体，行程长
     * 后坐力猛烈，有二次冲击，典型如 AK-47
     */
    @SerializedName("gas_long_stroke")
    GAS_LONG_STROKE,

    /**
     * 短行程导气式：活塞短行程推动枪机框
     * 后坐力较柔和，典型如 SKS、G36
     */
    @SerializedName("gas_short_stroke")
    GAS_SHORT_STROKE,

    /**
     * 气动式/直接导气式：燃气直接推动枪机框
     * 后坐力适中，典型如 M16/AR-15
     */
    @SerializedName("gas_piston")
    GAS_PISTON,

    /**
     * 滚轮延迟式：HK专利，利用滚轮将后坐力分散到较大时间窗口
     * 后坐力最柔和，典型如 HK G3
     */
    @SerializedName("roller_delayed")
    ROLLER_DELAYED,

    /**
     * 手动操作：栓动/泵动/拉栓等
     * 对应原 Bolt.MANUAL_ACTION
     */
    @SerializedName("manual_action")
    MANUAL_ACTION,

    /**
     * 未知/未指定
     */
    @SerializedName("unknown")
    UNKNOWN;

    public static final Codec<ActionType> CODEC = Codec.STRING.xmap(ActionType::valueOf, ActionType::name);
    public static final IntFunction<ActionType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, ActionType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ActionType::ordinal);

    /**
     */
    public static ActionType fromBolt(com.tacz.guns.resource.pojo.data.gun.Bolt bolt) {
        if (bolt == null) {
            return UNKNOWN;
        }
        return switch (bolt) {
            case OPEN_BOLT -> GAS_LONG_STROKE; // 默认映射：开膛待击→长行程导气
            case CLOSED_BOLT -> GAS_PISTON;    // 默认映射：闭膛待击→气动式
            case MANUAL_ACTION -> MANUAL_ACTION;
        };
    }

    /**
     * 后坐力累积系数（用于连发后坐力累积模型）
     */
    public float getRecoilAccumulationRate() {
        return switch (this) {
            case BLOWBACK -> 0.20f;
            case DELAYED_BLOWBACK -> 0.10f;
            case RECOIL_OPERATED -> 0.08f;
            case GAS_LONG_STROKE -> 0.15f;
            case GAS_SHORT_STROKE -> 0.10f;
            case GAS_PISTON -> 0.12f;
            case ROLLER_DELAYED -> 0.08f;
            case MANUAL_ACTION, UNKNOWN -> 0.0f;
        };
    }

    /**
     * 后坐力衰减率（每发后坐力的衰减比例）
     */
    public float getRecoilDecayRate() {
        return switch (this) {
            case BLOWBACK -> 0.03f;
            case DELAYED_BLOWBACK -> 0.05f;
            case RECOIL_OPERATED -> 0.06f;
            case GAS_LONG_STROKE -> 0.04f;
            case GAS_SHORT_STROKE -> 0.05f;
            case GAS_PISTON -> 0.05f;
            case ROLLER_DELAYED -> 0.06f;
            case MANUAL_ACTION, UNKNOWN -> 0.0f;
        };
    }

    /**
     * 恢复时间常数（毫秒），松开扳机后后坐力恢复到0的时间
     */
    public int getRecoveryTimeMs() {
        return switch (this) {
            case BLOWBACK -> 300;
            case DELAYED_BLOWBACK -> 400;
            case RECOIL_OPERATED -> 500;
            case GAS_LONG_STROKE -> 350;
            case GAS_SHORT_STROKE -> 400;
            case GAS_PISTON -> 380;
            case ROLLER_DELAYED -> 450;
            case MANUAL_ACTION, UNKNOWN -> 0;
        };
    }

    /**
     * 是否为自动循环原理（非手动操作）
     */
    public boolean isSelfLoading() {
        return this != MANUAL_ACTION && this != UNKNOWN;
    }
}
