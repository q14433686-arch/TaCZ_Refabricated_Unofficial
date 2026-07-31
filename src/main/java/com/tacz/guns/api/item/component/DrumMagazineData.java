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
 * 弹鼓数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>大容量可拆卸弹匣（50-100发）</li>
 *   <li>LIFO（后进先出）供弹，与盒式弹匣结构类似</li>
 *   <li>弹簧疲劳度：弹鼓弹簧比普通弹匣更易疲劳</li>
 *   <li>供弹口损伤度：同盒式弹匣</li>
 *   <li>发条张力：弹鼓特有的发条结构，张力影响供弹速度</li>
 *   <li>可靠性低（弹簧疲劳快，大容量结构复杂）</li>
 *   <li>笨重：换弹速度慢，影响移动速度</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 弹鼓
 */
public record DrumMagazineData(
        /** 弹药列表（LIFO，末尾为供弹口位置） */
        List<LoadedRound> rounds,
        /** 弹簧疲劳度（0.0 = 完好，1.0 = 完全疲劳） */
        float springFatigue,
        /** 供弹口损伤度（0.0 = 完好，1.0 = 严重变形） */
        float feedLipDamage,
        /** 发条张力（0.0 = 松弛，1.0 = 满张力） */
        float windingTension,
        /** 最大容量（50-100发） */
        int maxCapacity,
        /** 此弹鼓的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static DrumMagazineData create(int maxCapacity) {
        return new DrumMagazineData(new ArrayList<>(), 0.0f, 0.0f, 1.0f, maxCapacity, null);
    }

    public static DrumMagazineData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        return new DrumMagazineData(new ArrayList<>(), 0.0f, 0.0f, 1.0f, maxCapacity, cartridgeType);
    }

    public static final Codec<DrumMagazineData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LoadedRound.CODEC.listOf().fieldOf("rounds").forGetter(DrumMagazineData::rounds),
                    Codec.FLOAT.fieldOf("spring_fatigue").forGetter(DrumMagazineData::springFatigue),
                    Codec.FLOAT.fieldOf("feed_lip_damage").forGetter(DrumMagazineData::feedLipDamage),
                    Codec.FLOAT.fieldOf("winding_tension").forGetter(DrumMagazineData::windingTension),
                    Codec.INT.fieldOf("max_capacity").forGetter(DrumMagazineData::maxCapacity),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (rounds, fatigue, lipDmg, tension, cap, cartType) ->
                    new DrumMagazineData(rounds, fatigue, lipDmg, tension, cap, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, DrumMagazineData> STREAM_CODEC = StreamCodec.composite(
            LoadedRound.STREAM_CODEC.apply(ByteBufCodecs.list()), DrumMagazineData::rounds,
            ByteBufCodecs.FLOAT, DrumMagazineData::springFatigue,
            ByteBufCodecs.FLOAT, DrumMagazineData::feedLipDamage,
            ByteBufCodecs.FLOAT, DrumMagazineData::windingTension,
            ByteBufCodecs.INT, DrumMagazineData::maxCapacity,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (rounds, fatigue, lipDmg, tension, cap, cartType) ->
                    new DrumMagazineData(rounds, fatigue, lipDmg, tension, cap, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.DRUM;
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
     */
    public @Nullable LoadedRound feedRound() {
        if (rounds.isEmpty()) return null;
        return rounds.remove(rounds.size() - 1);
    }

    /**
     * 向弹鼓内压入一发弹药（LIFO：加入末尾）。
     */
    public DrumMagazineData loadRound(LoadedRound round) {
        if (isFull()) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;
        List<LoadedRound> newRounds = new ArrayList<>(rounds);
        newRounds.add(round);
        return new DrumMagazineData(newRounds, springFatigue, feedLipDamage, windingTension,
                maxCapacity, cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 增加弹簧疲劳度。
     * <p>
     * 弹鼓的弹簧疲劳速度比普通弹匣快 1.5 倍。
     */
    public DrumMagazineData withSpringFatigueAdded(float amount) {
        return new DrumMagazineData(rounds, Math.min(1.0f, springFatigue + amount * 1.5f),
                feedLipDamage, windingTension, maxCapacity, cartridgeType);
    }

    /**
     * 增加供弹口损伤度。
     */
    public DrumMagazineData withFeedLipDamageAdded(float amount) {
        return new DrumMagazineData(rounds, springFatigue,
                Math.min(1.0f, feedLipDamage + amount), windingTension, maxCapacity, cartridgeType);
    }

    /**
     * 消耗发条张力（每次供弹消耗少量张力）。
     */
    public DrumMagazineData withWindingTensionConsumed(float amount) {
        return new DrumMagazineData(rounds, springFatigue, feedLipDamage,
                Math.max(0.0f, windingTension - amount), maxCapacity, cartridgeType);
    }

    /**
     * 重新上发条（维护操作）。
     */
    public DrumMagazineData withWindingTensionRestored(float amount) {
        return new DrumMagazineData(rounds, springFatigue, feedLipDamage,
                Math.min(1.0f, windingTension + amount), maxCapacity, cartridgeType);
    }

    /**
     * 弹簧疲劳对供弹可靠性的修正（弹鼓比普通弹匣更易疲劳）
     */
    public float getSpringReliabilityModifier() {
        if (springFatigue < 0.2f) return 1.0f;
        if (springFatigue < 0.5f) return 0.85f;
        if (springFatigue < 0.7f) return 0.6f;
        return 0.3f;  // 严重疲劳
    }

    /**
     * 发条张力对供弹可靠性的修正。
     * <p>
     * 张力不足时供弹速度不够，可能导致供弹故障。
     */
    public float getWindingReliabilityModifier() {
        if (windingTension > 0.7f) return 1.0f;
        if (windingTension > 0.4f) return 0.85f;
        if (windingTension > 0.2f) return 0.6f;
        return 0.3f;  // 张力严重不足
    }

    /**
     * 供弹口损伤对供弹可靠性的修正
     */
    public float getFeedLipReliabilityModifier() {
        if (feedLipDamage < 0.2f) return 1.0f;
        if (feedLipDamage < 0.5f) return 0.85f;
        return 0.5f;
    }

    /**
     * 综合供弹可靠性（弹簧+供弹口+发条）
     */
    public float getOverallReliability() {
        return getSpringReliabilityModifier() * getFeedLipReliabilityModifier()
                * getWindingReliabilityModifier();
    }
}
