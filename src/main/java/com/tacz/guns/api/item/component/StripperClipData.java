package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.FeedSystemType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 桥夹（Stripper Clip）数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>固定容量，一次性消耗标记</li>
 *   <li>桥夹本身不进入枪内，仅用于将弹药快速压入枪内弹仓</li>
 *   <li>压入后桥夹变为空，标记为已消耗</li>
 *   <li>使用场景：Kar98k、M1 Garand 的桥夹供弹方式</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 桥夹
 */
public record StripperClipData(
        /** 弹药列表（有序，从头部开始压入枪内弹仓） */
        List<LoadedRound> rounds,
        /** 最大容量（通常5-10发） */
        int maxCapacity,
        /** 是否已消耗（弹药已全部压入枪内弹仓） */
        boolean isConsumed,
        /** 此桥夹的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static StripperClipData create(int maxCapacity) {
        return new StripperClipData(new ArrayList<>(), maxCapacity, false, null);
    }

    public static StripperClipData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        return new StripperClipData(new ArrayList<>(), maxCapacity, false, cartridgeType);
    }

    public static final Codec<StripperClipData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(StripperClipData::rounds),
                    Codec.INT.fieldOf("max_capacity").forGetter(StripperClipData::maxCapacity),
                    Codec.BOOL.fieldOf("is_consumed").forGetter(StripperClipData::isConsumed),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, cap, consumed, cartType) ->
                    new StripperClipData(rounds, cap, consumed, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, StripperClipData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), StripperClipData::rounds,
            ByteBufCodecs.INT, StripperClipData::maxCapacity,
            ByteBufCodecs.BOOL, StripperClipData::isConsumed,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, cap, consumed, cartType) ->
                    new StripperClipData(rounds, cap, consumed, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.STRIPPER_CLIP;
    }

    @Override
    public int getCapacity() {
        return maxCapacity;
    }

    @Override
    public int getCurrentRoundCount() {
        return rounds.size();
    }

    @Override
    public @Nullable Identifier getCartridgeType() {
        if (cartridgeType != null) return cartridgeType;
        if (!rounds.isEmpty()) return rounds.get(0).cartridgeType();
        return null;
    }

    // ====== 供弹操作 ======

    /**
     * 将桥夹内的所有弹药取出（一次性压入枪内弹仓）。
     * <p>
     * 取出后桥夹标记为已消耗。
     *
     * @return 包含所有弹药和消耗后桥夹的记录
     */
    public StripResult stripAll() {
        if (isConsumed || rounds.isEmpty()) {
            return new StripResult(List.of(), this);
        }
        List<LoadedRound> stripped = new ArrayList<>(rounds);
        StripperClipData consumedClip = new StripperClipData(
                new ArrayList<>(), maxCapacity, true, cartridgeType
        );
        return new StripResult(stripped, consumedClip);
    }

    /**
     * 向桥夹装入一发弹药。
     */
    public StripperClipData loadRound(LoadedRound round) {
        if (isFull() || isConsumed) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new StripperClipData(newRounds, maxCapacity, false,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 桥夹压入结果
     */
    public record StripResult(List<LoadedRound> rounds, StripperClipData remainingClip) {}
}
