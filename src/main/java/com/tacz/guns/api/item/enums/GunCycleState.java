package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 枪机循环状态枚举。
 * <p>
 * 对应设计文档：E.2.1 完整枪机循环状态机
 * <p>
 * 描述半自动/自动枪械射击循环的完整状态，包括正常循环和故障状态。
 */
public enum GunCycleState {
    /**
     * 空仓挂机：弹匣空，枪机锁定后方
     */
    @SerializedName("empty")
    EMPTY,

    /**
     * 待发：枪机闭锁，弹在膛内，等待扣扳机
     */
    @SerializedName("ready")
    READY,

    /**
     * 击发中：击针已撞击底火，等待膛压建立
     */
    @SerializedName("firing")
    FIRING,

    /**
     * 开锁中：枪机正在开锁
     */
    @SerializedName("unlocking")
    UNLOCKING,

    /**
     * 抽壳中：枪机后拉，抽壳中
     */
    @SerializedName("extracting")
    EXTRACTING,

    /**
     * 抛壳中：弹壳正在被抛出
     */
    @SerializedName("ejecting")
    EJECTING,

    /**
     * 供弹中：枪机前进，推送新弹入膛
     */
    @SerializedName("feeding")
    FEEDING,

    /**
     * 闭锁中：枪机正在闭锁
     */
    @SerializedName("locking")
    LOCKING,

    /**
     * 故障状态：卡壳/双重进弹等
     */
    @SerializedName("malfunction")
    MALFUNCTION,

    /**
     * 枪机打开：手动拉栓/空仓挂机
     */
    @SerializedName("bolt_open")
    BOLT_OPEN,

    /**
     * 枪机闭锁：手动推栓到位
     */
    @SerializedName("bolt_close")
    BOLT_CLOSE;

    public static final Codec<GunCycleState> CODEC = Codec.STRING.xmap(GunCycleState::valueOf, GunCycleState::name);
    public static final IntFunction<GunCycleState> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, GunCycleState> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, GunCycleState::ordinal);

    /**
     * 是否为正常射击循环中的状态（非故障/非手动操作）
     */
    public boolean isCyclingState() {
        return this == FIRING || this == UNLOCKING || this == EXTRACTING
                || this == EJECTING || this == FEEDING || this == LOCKING;
    }

    /**
     * 是否可以射击（仅 READY 状态可射击）
     */
    public boolean canFire() {
        return this == READY;
    }
}
