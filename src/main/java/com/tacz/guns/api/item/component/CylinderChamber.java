package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.ChamberState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * 转轮弹巢的单格弹膛数据。
 * <p>
 * 转轮弹巢的每格弹膛独立跟踪状态：
 * <ul>
 *   <li>EMPTY — 空格，可装入新弹</li>
 *   <li>LOADED — 已装入一发弹药，可击发</li>
 *   <li>SPENT — 已击发，弹壳留在膛内，需抛壳后才能装新弹</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化 — 转轮弹巢
 */
public record CylinderChamber(
        /** 弹膛状态 */
        ChamberState state,
        /** 弹膛内的弹药（仅 LOADED/SPENT 状态有值） */
        @Nullable LoadedRound round
) {
    /** 空格弹膛 */
    public static CylinderChamber empty() {
        return new CylinderChamber(ChamberState.EMPTY, null);
    }

    /** 装入弹药的弹膛 */
    public static CylinderChamber loaded(LoadedRound round) {
        return new CylinderChamber(ChamberState.LOADED, round);
    }

    public static final Codec<CylinderChamber> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ChamberState.CODEC.fieldOf("state").forGetter(CylinderChamber::state),
                    LoadedRound.CODEC.optionalFieldOf("round").forGetter(c ->
                            java.util.Optional.ofNullable(c.round()))
            ).apply(instance, (state, roundOpt) ->
                    new CylinderChamber(state, roundOpt.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, CylinderChamber> STREAM_CODEC = StreamCodec.composite(
            ChamberState.STREAM_CODEC, CylinderChamber::state,
            ByteBufCodecs.optional(LoadedRound.STREAM_CODEC), c -> java.util.Optional.ofNullable(c.round()),
            (state, roundOpt) -> new CylinderChamber(state, roundOpt.orElse(null))
    );

    /**
     * 击发此弹膛：LOADED → SPENT
     */
    public CylinderChamber fire() {
        if (state != ChamberState.LOADED) return this;
        return new CylinderChamber(ChamberState.SPENT, round);
    }

    /**
     * 抛壳：SPENT → EMPTY
     */
    public CylinderChamber eject() {
        if (state != ChamberState.SPENT) return this;
        return new CylinderChamber(ChamberState.EMPTY, null);
    }

    /**
     * 装入弹药：EMPTY → LOADED
     */
    public CylinderChamber load(LoadedRound round) {
        if (state != ChamberState.EMPTY) return this;
        return new CylinderChamber(ChamberState.LOADED, round);
    }

    /** 是否可以装入新弹 */
    public boolean canLoad() {
        return state.canLoad();
    }

    /** 是否可以击发 */
    public boolean canFire() {
        return state.canFire();
    }
}
