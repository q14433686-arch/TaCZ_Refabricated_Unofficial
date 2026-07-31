package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.BeltLinkType;
import com.tacz.guns.api.item.enums.FeedSystemType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹链数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>FIFO（先进先出）供弹：先装入的弹先被供入枪膛</li>
 *   <li>链节类型：可散式（Disintegrating）或不可散式（Non-disintegrating）</li>
 *   <li>可对接标记：是否可连接下一条弹药箱实现连续供弹</li>
 *   <li>可靠性低（需润滑，机枪类武器）</li>
 *   <li>大容量（50-200发）</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 弹链
 */
public record BeltData(
        /** 弹药列表（FIFO，列表头部为最早装入的弹，先被取出） */
        List<LoadedRound> rounds,
        /** 链节类型 */
        BeltLinkType linkType,
        /** 是否可对接下一条弹药箱 */
        boolean canChain,
        /** 最大容量 */
        int maxCapacity,
        /** 此弹链的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static BeltData create(int maxCapacity, BeltLinkType linkType) {
        return new BeltData(new ArrayList<>(), linkType, false, maxCapacity, null);
    }

    public static BeltData create(int maxCapacity, BeltLinkType linkType, boolean canChain) {
        return new BeltData(new ArrayList<>(), linkType, canChain, maxCapacity, null);
    }

    public static final Codec<BeltData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(BeltData::rounds),
                    BeltLinkType.CODEC.fieldOf("link_type").forGetter(BeltData::linkType),
                    Codec.BOOL.fieldOf("can_chain").forGetter(BeltData::canChain),
                    Codec.INT.fieldOf("max_capacity").forGetter(BeltData::maxCapacity),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, linkType, canChain, cap, cartType) ->
                    new BeltData(rounds, linkType, canChain, cap, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, BeltData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), BeltData::rounds,
            BeltLinkType.STREAM_CODEC, BeltData::linkType,
            ByteBufCodecs.BOOL, BeltData::canChain,
            ByteBufCodecs.INT, BeltData::maxCapacity,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, linkType, canChain, cap, cartType) ->
                    new BeltData(rounds, linkType, canChain, cap, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.BELT;
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
     * 从弹链取出一发弹药（FIFO：取头部）。
     * @return 取出的弹药，弹链为空则返回 null
     */
    public @Nullable LoadedRound feedRound() {
        if (rounds.isEmpty()) return null;
        return rounds.remove(0);
    }

    /**
     * 向弹链尾部加入一发弹药（FIFO：加入尾部）。
     */
    public BeltData loadRound(LoadedRound round) {
        if (isFull()) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new BeltData(newRounds, linkType, canChain, maxCapacity,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 对接另一条弹链（将另一条弹链的弹药追加到当前弹链尾部）。
     * <p>
     * 仅当 canChain 为 true 时允许对接。
     * 对接后的总弹药数不超过当前弹链的容量。
     */
    public BeltData chainBelt(BeltData other) {
        if (!canChain) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        for (LoadedRound round : other.rounds) {
            if (newRounds.size() >= maxCapacity) break;
            if (isCartridgeCompatible(round.cartridgeType())) {
                newRounds.add(round);
            }
        }
        return new BeltData(newRounds, linkType, canChain, maxCapacity, cartridgeType);
    }

    /**
     * 链节类型对供弹可靠性的修正（0.0~1.0）
     * <p>
     * 可散式弹链在高速射击时链节可能卡住，可靠性略低。
     */
    public float getLinkReliabilityModifier() {
        return switch (linkType) {
            case DISINTEGRATING -> 0.9f;
            case NON_DISINTEGRATING -> 1.0f;
        };
    }
}
