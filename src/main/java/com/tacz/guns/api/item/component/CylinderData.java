package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.ChamberState;
import com.tacz.guns.api.item.enums.FeedSystemType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 转轮弹巢数据。
 * <p>
 * 特性：
 * <ul>
 *   <li>固定长度数组，每格弹膛独立状态：空/实弹/待抛壳</li>
 *   <li>记录当前对齐枪管的格位索引（击发/对准的弹膛）</li>
 *   <li>固定在枪上，不可拆卸</li>
 *   <li>可靠性极高（机械结构简单，无弹簧供弹）</li>
 *   <li>装弹方式：逐发装填或快速装弹器</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 转轮弹巢
 */
public record CylinderData(
        /** 弹膛列表，固定长度，每格独立状态 */
        List<CylinderChamber> chambers,
        /** 当前对齐枪管的格位索引（0-based） */
        int alignedChamberIndex,
        /** 最大容量（通常5-8） */
        int maxCapacity,
        /** 此弹巢的口径规格 */
        @Nullable Identifier cartridgeType
) implements FeedDeviceData {

    public static CylinderData create(int maxCapacity) {
        List<CylinderChamber> chambers = new ArrayList<>();
        for (int i = 0; i < maxCapacity; i++) {
            chambers.add(CylinderChamber.empty());
        }
        return new CylinderData(chambers, 0, maxCapacity, null);
    }

    public static CylinderData create(int maxCapacity, @Nullable Identifier cartridgeType) {
        List<CylinderChamber> chambers = new ArrayList<>();
        for (int i = 0; i < maxCapacity; i++) {
            chambers.add(CylinderChamber.empty());
        }
        return new CylinderData(chambers, 0, maxCapacity, cartridgeType);
    }

    public static final Codec<CylinderData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    CylinderChamber.CODEC.listOf().fieldOf("chambers").forGetter(CylinderData::chambers),
                    Codec.INT.fieldOf("aligned_index").forGetter(CylinderData::alignedChamberIndex),
                    Codec.INT.fieldOf("max_capacity").forGetter(CylinderData::maxCapacity),
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType()))
            ).apply(instance, (chambers, idx, cap, cartType) ->
                    new CylinderData(chambers, idx, cap, cartType.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, CylinderData> STREAM_CODEC = StreamCodec.composite(
            CylinderChamber.STREAM_CODEC.apply(ByteBufCodecs.list()), CylinderData::chambers,
            ByteBufCodecs.INT, CylinderData::alignedChamberIndex,
            ByteBufCodecs.INT, CylinderData::maxCapacity,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            (chambers, idx, cap, cartType) ->
                    new CylinderData(chambers, idx, cap, cartType.orElse(null))
    );

    @Override
    public FeedSystemType getFeedSystemType() {
        return FeedSystemType.CYLINDER;
    }

    @Override
    public int getCapacity() {
        return maxCapacity;
    }

    @Override
    public int getCurrentRoundCount() {
        return (int) chambers.stream().filter(c -> c.state() == ChamberState.LOADED).count();
    }

    @Override
    public @Nullable Identifier getCartridgeType() {
        if (cartridgeType != null) return cartridgeType;
        for (CylinderChamber chamber : chambers) {
            if (chamber.round() != null) return chamber.round().cartridgeType();
        }
        return null;
    }

    // ====== 供弹操作 ======

    /**
     * 获取当前对齐枪管的弹膛。
     */
    public CylinderChamber getAlignedChamber() {
        if (alignedChamberIndex < 0 || alignedChamberIndex >= chambers.size()) return null;
        return chambers.get(alignedChamberIndex);
    }

    /**
     * 从对齐枪管的弹膛取出弹药用于击发。
     * <p>
     * 击发后弹膛变为 SPENT 状态，弹药留在膛内。
     *
     * @return 击发的弹药，或 null（无法击发）
     */
    public @Nullable LoadedRound feedRound() {
        CylinderChamber aligned = getAlignedChamber();
        if (aligned == null || !aligned.canFire()) return null;
        return aligned.round();
    }

    /**
     * 击发当前对齐枪管的弹膛。
     * <p>
     * 击发后弹膛状态变为 SPENT，然后转轮旋转到下一格。
     *
     * @return 击发后的新数据，或 this（无法击发）
     */
    public CylinderData fireAndRotate() {
        CylinderChamber aligned = getAlignedChamber();
        if (aligned == null || !aligned.canFire()) return this;

        List<CylinderChamber> newChambers = new ArrayList<>(chambers);
        newChambers.set(alignedChamberIndex, aligned.fire());

        // 旋转到下一格
        int nextIndex = (alignedChamberIndex + 1) % maxCapacity;
        return new CylinderData(newChambers, nextIndex, maxCapacity, cartridgeType);
    }

    /**
     * 向指定弹膛装入弹药。
     *
     * @param chamberIndex 弹膛索引
     * @param round        要装入的弹药
     * @return 装入后的新数据
     */
    public CylinderData loadRound(int chamberIndex, LoadedRound round) {
        if (chamberIndex < 0 || chamberIndex >= chambers.size()) return this;
        CylinderChamber chamber = chambers.get(chamberIndex);
        if (!chamber.canLoad()) return this;
        if (!isCartridgeCompatible(round.cartridgeType())) return this;

        List<CylinderChamber> newChambers = new ArrayList<>(chambers);
        newChambers.set(chamberIndex, chamber.load(round));

        return new CylinderData(newChambers, alignedChamberIndex, maxCapacity,
                cartridgeType != null ? cartridgeType : round.cartridgeType());
    }

    /**
     * 向下一个空弹膛装入弹药。
     *
     * @return 装入后的新数据
     */
    public CylinderData loadNextEmpty(LoadedRound round) {
        for (int i = 0; i < chambers.size(); i++) {
            if (chambers.get(i).canLoad()) {
                return loadRound(i, round);
            }
        }
        return this;
    }

    /**
     * 抛出指定弹膛的弹壳。
     *
     * @param chamberIndex 弹膛索引
     * @return 抛壳后的新数据
     */
    public CylinderData ejectChamber(int chamberIndex) {
        if (chamberIndex < 0 || chamberIndex >= chambers.size()) return this;
        CylinderChamber chamber = chambers.get(chamberIndex);
        if (!chamber.needsEject()) return this;

        List<CylinderChamber> newChambers = new ArrayList<>(chambers);
        newChambers.set(chamberIndex, chamber.eject());

        return new CylinderData(newChambers, alignedChamberIndex, maxCapacity, cartridgeType);
    }

    /**
     * 抛出所有已击发的弹壳。
     */
    public CylinderData ejectAllSpent() {
        List<CylinderChamber> newChambers = new ArrayList<>(chambers);
        for (int i = 0; i < newChambers.size(); i++) {
            if (newChambers.get(i).needsEject()) {
                newChambers.set(i, newChambers.get(i).eject());
            }
        }
        return new CylinderData(newChambers, alignedChamberIndex, maxCapacity, cartridgeType);
    }

    /**
     * 旋转到指定弹膛。
     */
    public CylinderData withAlignedIndex(int index) {
        return new CylinderData(chambers, index % maxCapacity, maxCapacity, cartridgeType);
    }

    /**
     * 获取待抛壳的弹膛数量。
     */
    public int getSpentChamberCount() {
        return (int) chambers.stream().filter(c -> c.state() == ChamberState.SPENT).count();
    }

    /**
     * 获取空弹膛数量。
     */
    public int getEmptyChamberCount() {
        return (int) chambers.stream().filter(c -> c.state() == ChamberState.EMPTY).count();
    }
}
