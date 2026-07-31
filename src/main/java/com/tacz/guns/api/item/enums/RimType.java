package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 底缘类型枚举。
 * <p>
 * 决定弹壳底部几何形态，影响抽壳钩兼容性和弹匣/弹仓设计。
 * 这是口径的恒定物理事实，不会因装药/弹头类型而改变。
 */
public enum RimType {
    /** 无凸缘（Rimless）：弹壳底部直径与弹壳体直径相同，现代步枪/手枪弹最常见 */
    @SerializedName("rimless")
    RIMLESS,

    /** 凸缘（Rimmed）：弹壳底部有明显的凸出边缘，常见于老式步枪弹和霰弹 */
    @SerializedName("rimmed")
    RIMMED,

    /** 半凸缘（Semi-rimmed）：底部略有凸出，但比凸缘小 */
    @SerializedName("semi_rimmed")
    SEMI_RIMMED,

    /** 缩缘（Rebated）：底部直径小于弹壳体直径，常见于大口径专用弹 */
    @SerializedName("rebated")
    REBATED,

    /** 带式（Belted）：弹壳底部前方有一圈加强带，常见于马格南步枪弹 */
    @SerializedName("belted")
    BELTED;

    public static final Codec<RimType> CODEC = Codec.STRING.xmap(RimType::valueOf, RimType::name);
    public static final IntFunction<RimType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, RimType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, RimType::ordinal);

    /**
     * 是否需要抽壳钩适配凸缘。
     * <p>
     * RIMMED 和 SEMI_RIMMED 的凸缘会被抽壳钩勾住，对抽壳钩设计有要求。
     */
    public boolean requiresRimExtractor() {
        return this == RIMMED || this == SEMI_RIMMED;
    }

    /**
     * 是否可以在管状弹仓中安全排列。
     * <p>
     * RIMMED 弹在管状弹仓中需要"底缘对底缘"排列（防止底缘撞击前方弹药的底火），
     * 而 RIMLESS 弹没有这个问题。
     */
    public boolean isSafeInTubularMagazine() {
        return this == RIMLESS || this == REBATED;
    }
}
