package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 携行具类型枚举。
 * <p>
 * 对应设计文档：L.2.2 携行具系统
 */
public enum CarryGearType {
    @SerializedName("none")
    NONE(2, 1.0f),

    @SerializedName("chest_rig")
    CHEST_RIG(6, 1.2f),

    @SerializedName("waist_pouch")
    WAIST_POUCH(4, 1.1f),

    @SerializedName("backpack")
    BACKPACK(10, 0.8f),

    @SerializedName("tactical_vest")
    TACTICAL_VEST(8, 1.3f);

    public static final Codec<CarryGearType> CODEC = Codec.STRING.xmap(CarryGearType::valueOf, CarryGearType::name);
    public static final IntFunction<CarryGearType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, CarryGearType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, CarryGearType::ordinal);

    private final int magazineCapacity;
    private final float reloadSpeedModifier;

    CarryGearType(int magazineCapacity, float reloadSpeedModifier) {
        this.magazineCapacity = magazineCapacity;
        this.reloadSpeedModifier = reloadSpeedModifier;
    }

    /**
     * 弹匣携带上限
     */
    public int getMagazineCapacity() {
        return magazineCapacity;
    }

    /**
     * 换弹速度修正系数
     */
    public float getReloadSpeedModifier() {
        return reloadSpeedModifier;
    }
}
