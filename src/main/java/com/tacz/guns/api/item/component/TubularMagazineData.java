package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.FeedSystemType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 管状弹仓数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>严格 FIFO（先进先出）队列：先装入的弹先被供入枪膛</li>
 *   <li>固定在枪管下方，不可拆卸</li>
 *   <li>逐发从装弹口塞入，装填速度较慢</li>
 *   <li>可靠性高（泵动霰弹枪/管状弹仓步枪）</li>
 *   <li>注意：管状弹仓不能使用尖头弹（有膛内引爆风险）</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 管状弹仓
 */
public record TubularMagazineData(
        /** 弹药列表（FIFO，列表头部为最早装入的弹，先被取出） */
        List<LoadedRound> rounds,
        /** 最大容量 */
        int maxCapacity,
        /** 此弹仓的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static TubularMagazineData create(int maxCapacity) {
        return new TubularMagazineData(new ArrayList<>(), maxCapacity, null);
    }

    public static TubularMagazineData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        return new TubularMagazineData(new ArrayList<>(), maxCapacity, cartridgeType);
    }

    public static final Codec<TubularMagazineData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(TubularMagazineData::rounds),
                    Codec.INT.fieldOf("max_capacity").forGetter(TubularMagazineData::maxCapacity),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, cap, cartType) ->
                    new TubularMagazineData(rounds, cap, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, TubularMagazineData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), TubularMagazineData::rounds,
            ByteBufCodecs.INT, TubularMagazineData::maxCapacity,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, cap, cartType) ->
                    new TubularMagazineData(rounds, cap, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.TUBULAR_MAGAZINE;
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
     * 从管状弹仓取出一发弹药（FIFO：取头部，即最早装入的）。
     * @return 取出的弹药，弹仓为空则返回 null
     */
    public @Nullable LoadedRound feedRound() {
        if (rounds.isEmpty()) return null;
        return rounds.remove(0);
    }

    /**
     * 向管状弹仓尾部塞入一发弹药（FIFO：加入尾部）。
     * @return 装入成功后的新数据，弹仓满则返回 this
     */
    public TubularMagazineData loadRound(LoadedRound round) {
        if (isFull()) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new TubularMagazineData(newRounds, maxCapacity,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 检查弹药是否可安全装入管状弹仓。
     * <p>
     * 管状弹仓不能使用尖头弹（如 AP 弹），因为尖头弹在弹仓内
     * 可能撞击后方弹药的底火，导致膛内引爆。
     * <p>
     * 此处仅做数据层面的警告，实际限制由游戏逻辑层处理。
     */
    public boolean isPointedBulletWarning(LoadedRound round) {
        return round.bulletType() == com.tacz.guns.api.item.enums.BulletType.AP;
    }
}
