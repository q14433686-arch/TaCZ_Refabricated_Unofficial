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
import java.util.Collections;
import java.util.List;

/**
 * 盒式弹匣（可拆卸弹匣）数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>有序列表，LIFO（后进先出）供弹：列表末尾为供弹口位置</li>
 *   <li>弹簧疲劳度：影响供弹可靠性，随使用次数增加</li>
 *   <li>供弹口损伤度：粗暴操作可能导致供弹口变形，影响供弹可靠性</li>
 *   <li>可拆卸：从枪上取下后作为独立物品存在</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 可拆卸弹匣
 */
public record BoxMagazineData(
        /** 弹药列表（LIFO，末尾为供弹口位置，即最后加入的先被取出） */
        List<LoadedRound> rounds,
        /** 弹簧疲劳度（0.0 = 完好，1.0 = 完全疲劳） */
        float springFatigue,
        /** 供弹口损伤度（0.0 = 完好，1.0 = 严重变形） */
        float feedLipDamage,
        /** 最大容量 */
        int maxCapacity,
        /** 此弹匣的口径规格（从第一发弹药推断，空弹匣时为 null） */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static BoxMagazineData create(int maxCapacity) {
        return new BoxMagazineData(new ArrayList<>(), 0.0f, 0.0f, maxCapacity, null);
    }

    public static BoxMagazineData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        return new BoxMagazineData(new ArrayList<>(), 0.0f, 0.0f, maxCapacity, cartridgeType);
    }

    public static final Codec<BoxMagazineData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(BoxMagazineData::rounds),
                    Codec.FLOAT.fieldOf("spring_fatigue").forGetter(BoxMagazineData::springFatigue),
                    Codec.FLOAT.fieldOf("feed_lip_damage").forGetter(BoxMagazineData::feedLipDamage),
                    Codec.INT.fieldOf("max_capacity").forGetter(BoxMagazineData::maxCapacity),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, fatigue, lipDmg, cap, cartType) ->
                    new BoxMagazineData(rounds, fatigue, lipDmg, cap, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, BoxMagazineData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), BoxMagazineData::rounds,
            ByteBufCodecs.FLOAT, BoxMagazineData::springFatigue,
            ByteBufCodecs.FLOAT, BoxMagazineData::feedLipDamage,
            ByteBufCodecs.INT, BoxMagazineData::maxCapacity,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, fatigue, lipDmg, cap, cartType) ->
                    new BoxMagazineData(rounds, fatigue, lipDmg, cap, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.DETACHABLE_MAGAZINE;
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
     * 从供弹口取出一发弹药（LIFO：取末尾）。
     * @return 取出的弹药，弹匣为空则返回 null
     */
    public @Nullable LoadedRound feedRound() {
        if (rounds.isEmpty()) return null;
        return rounds.remove(rounds.size() - 1);
    }

    /**
     * 向弹匣内压入一发弹药（LIFO：加入末尾）。
     * @return 压入成功后的新数据，弹匣满则返回 this
     */
    public BoxMagazineData loadRound(LoadedRound round) {
        if (isFull()) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new BoxMagazineData(newRounds, springFatigue, feedLipDamage, maxCapacity,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 一次性装入所有弹药（从 AmmoData 批量转换）。
     * 不超过弹匣容量。
     */
    public BoxMagazineData loadRounds(List<LoadedRound> roundsToLoad) {
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        for (LoadedRound round : roundsToLoad) {
            if (newRounds.size() >= maxCapacity) break;
            if (!isCartridgeCompatible(round.cartridgeType())) continue;
            newRounds.add(round);
        }
        return new BoxMagazineData(newRounds, springFatigue, feedLipDamage, maxCapacity,
                newRounds.isEmpty() ? cartridgeType : newRounds.get(0).cartridgeType());
    }

    /**
     * 增加弹簧疲劳度（每次供弹循环+1）
     */
    public BoxMagazineData withSpringFatigueAdded(float amount) {
        return new BoxMagazineData(rounds, Math.min(1.0f, springFatigue + amount),
                feedLipDamage, maxCapacity, cartridgeType);
    }

    /**
     * 增加供弹口损伤度
     */
    public BoxMagazineData withFeedLipDamageAdded(float amount) {
        return new BoxMagazineData(rounds, springFatigue,
                Math.min(1.0f, feedLipDamage + amount), maxCapacity, cartridgeType);
    }

    /**
     * 弹簧疲劳对供弹可靠性的修正（0.0~1.0）
     */
    public float getSpringReliabilityModifier() {
        if (springFatigue < 0.3f) return 1.0f;
        if (springFatigue < 0.6f) return 0.9f;
        if (springFatigue < 0.8f) return 0.7f;
        return 0.4f;  // 严重疲劳
    }

    /**
     * 供弹口损伤对供弹可靠性的修正（0.0~1.0）
     */
    public float getFeedLipReliabilityModifier() {
        if (feedLipDamage < 0.2f) return 1.0f;
        if (feedLipDamage < 0.5f) return 0.85f;
        return 0.5f;  // 严重变形
    }

    /**
     * 综合供弹可靠性（弹簧+供弹口）
     */
    public float getOverallReliability() {
        return getSpringReliabilityModifier() * getFeedLipReliabilityModifier();
    }
}
