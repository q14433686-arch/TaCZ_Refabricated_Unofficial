package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 底火类型枚举。
 * <p>
 * 对应设计文档：B.2.2 底火类型
 * <p>
 * Boxer式底火弹壳只有一个底火孔，复装时可用工具顶出废底火，因此可复装；
 * Berdan式底火弹壳有两个底火孔，复装时无法取出废底火，因此通常不可复装。
 */
public enum PrimerType {
    /**
     * Boxer式底火：中心一个底火砧，弹壳只有一个底火孔
     * 可复装体系中使用
     */
    @SerializedName("boxer")
    BOXER,

    /**
     * Berdan式底火：两个偏心底火孔，底火砧在弹壳内部
     * 不可复装体系中使用（军事弹药普遍采用）
     */
    @SerializedName("berdan")
    BERDAN,

    /**
     * 马格南底火：更强的点火能量，用于大口径/高膛压弹药
     * 确保大装药量下发射药充分点燃，减少哑弹概率
     */
    @SerializedName("magnum")
    MAGNUM;

    public static final Codec<PrimerType> CODEC = Codec.STRING.xmap(PrimerType::valueOf, PrimerType::name);
    public static final IntFunction<PrimerType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, PrimerType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, PrimerType::ordinal);

    /**
     * 是否支持复装（Boxer式支持，Berdan式和Magnum式不支持）
     */
    public boolean isReloadable() {
        return this == BOXER;
    }

    /**
     * 点火可靠性（影响哑弹概率）
     * Magnum > Boxer > Berdan
     */
    public float getIgnitionReliability() {
        return switch (this) {
            case BOXER -> 0.99f;
            case BERDAN -> 0.97f;
            case MAGNUM -> 0.995f;
        };
    }

    /**
     * 点火能量修正（影响发射药燃速和膛压曲线）
     * Magnum底火更强力的冲击使发射药更充分燃烧
     */
    public float getIgnitionEnergyModifier() {
        return switch (this) {
            case BOXER -> 1.0f;
            case BERDAN -> 0.98f;
            case MAGNUM -> 1.05f;
        };
    }

    /**
     * 所需最低科技阶段
     */
    public int getMinTechLevel() {
        return switch (this) {
            case BOXER -> 1;   // T1+
            case BERDAN -> 1;  // T1+
            case MAGNUM -> 2;  // T2+
        };
    }
}
