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
 * 漏夹（En Bloc Clip）数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>固定容量，整体插入弹仓</li>
 *   <li>强制整体弹出标记：当最后一发弹药被取出后，漏夹自动弹出</li>
 *   <li>与桥夹不同，漏夹整体进入枪内弹仓，弹药和漏夹一起在枪内</li>
 *   <li>使用场景：M1 Garand 的 en bloc clip</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 漏夹
 */
public record EnBlocClipData(
        /** 弹药列表（有序，从头部开始供弹） */
        List<LoadedRound> rounds,
        /** 最大容量（通常5-8发） */
        int maxCapacity,
        /** 是否已弹出（最后一发取出后自动弹出） */
        boolean autoEject,
        /** 此漏夹的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static EnBlocClipData create(int maxCapacity) {
        return new EnBlocClipData(new ArrayList<>(), maxCapacity, false, null);
    }

    public static EnBlocClipData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        return new EnBlocClipData(new ArrayList<>(), maxCapacity, false, cartridgeType);
    }

    public static final Codec<EnBlocClipData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(EnBlocClipData::rounds),
                    Codec.INT.fieldOf("max_capacity").forGetter(EnBlocClipData::maxCapacity),
                    Codec.BOOL.fieldOf("auto_eject").forGetter(EnBlocClipData::autoEject),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, cap, eject, cartType) ->
                    new EnBlocClipData(rounds, cap, eject, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, EnBlocClipData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), EnBlocClipData::rounds,
            ByteBufCodecs.INT, EnBlocClipData::maxCapacity,
            ByteBufCodecs.BOOL, EnBlocClipData::autoEject,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, cap, eject, cartType) ->
                    new EnBlocClipData(rounds, cap, eject, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.EN_BLOC;
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
     * 从漏夹取出一发弹药（FIFO：取头部）。
     * <p>
     * 当最后一发弹药被取出后，漏夹自动弹出（autoEject = true）。
     *
     * @return 供弹结果：取出的弹药 + 更新后的漏夹数据
     */
    public FeedResult feedRound() {
        if (rounds.isEmpty()) return new FeedResult(null, this);
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        LoadedRound round = newRounds.remove(0);
        // 最后一发取出后自动弹出
        boolean shouldEject = newRounds.isEmpty();
        return new FeedResult(round, new EnBlocClipData(
                newRounds, maxCapacity, shouldEject, cartridgeType
        ));
    }

    /**
     * 向漏夹装入一发弹药。
     */
    public EnBlocClipData loadRound(LoadedRound round) {
        if (isFull() || autoEject) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new EnBlocClipData(newRounds, maxCapacity, false,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 供弹结果
     */
    public record FeedResult(@Nullable LoadedRound round, EnBlocClipData remainingClip) {}
}
