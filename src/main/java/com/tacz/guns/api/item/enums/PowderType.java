package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 发射药类型枚举。
 * <p>
 * 对应设计文档：B.2.3 发射药类型
 */
public enum PowderType {
    /**
     * 黑火药：积碳快、烟雾大、暴露位置
     * T0-T1阶段使用，生产链简单
     */
    @SerializedName("black_powder")
    BLACK_POWDER,

    /**
     * 无烟发射药：高性能但生产链更长
     * T3+阶段使用，硝化棉基
     */
    @SerializedName("smokeless")
    SMOKELESS,

    /**
     * 双基发射药：硝化棉+硝化甘油
     * 燃烧效率更高，能量密度比单基药高约20%
     * 适用于步枪弹和机枪弹，需更精密的制造工艺
     */
    @SerializedName("double_base")
    DOUBLE_BASE,

    /**
     * 三基发射药：硝化棉+硝化甘油+硝基胍
     * 烧蚀最低、枪口焰最小，膛压曲线最平缓
     * 高级军用弹药使用，T4+阶段
     */
    @SerializedName("triple_base")
    TRIPLE_BASE;

    public static final Codec<PowderType> CODEC = Codec.STRING.xmap(PowderType::valueOf, PowderType::name);
    public static final IntFunction<PowderType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, PowderType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, PowderType::ordinal);

    /**
     * 积碳速率（每发射击的积碳增量）
     */
    public float getCarbonFoulingRate() {
        return switch (this) {
            case BLACK_POWDER -> 0.5f;
            case SMOKELESS -> 0.05f;
            case DOUBLE_BASE -> 0.04f;
            case TRIPLE_BASE -> 0.02f;
        };
    }

    /**
     * 烟雾效果等级（0=无，1=小，2=大）
     */
    public int getSmokeLevel() {
        return switch (this) {
            case BLACK_POWDER -> 2;
            case SMOKELESS -> 0;
            case DOUBLE_BASE -> 0;
            case TRIPLE_BASE -> 0;
        };
    }

    /**
     * 声音修正系数
     */
    public float getSoundModifier() {
        return switch (this) {
            case BLACK_POWDER -> 1.2f;
            case SMOKELESS -> 1.0f;
            case DOUBLE_BASE -> 1.0f;
            case TRIPLE_BASE -> 0.95f;
        };
    }

    /**
     * 热量产生系数（每发射击产生的热量倍率）
     */
    public float getHeatModifier() {
        return switch (this) {
            case BLACK_POWDER -> 1.5f;
            case SMOKELESS -> 1.0f;
            case DOUBLE_BASE -> 1.1f;
            case TRIPLE_BASE -> 0.85f;
        };
    }

    /**
     * 所需最低科技阶段
     */
    public int getMinTechLevel() {
        return switch (this) {
            case BLACK_POWDER -> 0;  // T0
            case SMOKELESS -> 3;     // T3
            case DOUBLE_BASE -> 3;   // T3
            case TRIPLE_BASE -> 4;   // T4
        };
    }

    /**
     * 能量密度系数（相对于标准无烟火药）
     * 影响初速计算：相同装药量下，能量密度越高初速越高
     */
    public float getEnergyDensity() {
        return switch (this) {
            case BLACK_POWDER -> 0.4f;
            case SMOKELESS -> 1.0f;
            case DOUBLE_BASE -> 1.2f;
            case TRIPLE_BASE -> 1.15f;
        };
    }

    /**
     * 燃速等级（1=最慢，5=最快）
     * 快燃药适合短枪管，慢燃药适合长枪管
     * 不匹配时：快燃药配长枪管→过压风险；慢燃药配短枪管→初速下降
     */
    public int getBurnRateClass() {
        return switch (this) {
            case BLACK_POWDER -> 5;      // 黑火药燃速最快
            case SMOKELESS -> 3;         // 标准无烟火药
            case DOUBLE_BASE -> 2;       // 双基药偏慢
            case TRIPLE_BASE -> 1;       // 三基药最慢
        };
    }

    /**
     * 烧蚀系数（对枪管的磨损倍率）
     * 三基药烧蚀最低，黑火药烧蚀最高
     */
    public float getErosionRate() {
        return switch (this) {
            case BLACK_POWDER -> 2.0f;
            case SMOKELESS -> 1.0f;
            case DOUBLE_BASE -> 1.2f;
            case TRIPLE_BASE -> 0.6f;
        };
    }

    /**
     * 枪口焰等级（0=无，1=小，2=中，3=大）
     */
    public int getMuzzleFlashLevel() {
        return switch (this) {
            case BLACK_POWDER -> 3;
            case SMOKELESS -> 1;
            case DOUBLE_BASE -> 2;
            case TRIPLE_BASE -> 0;
        };
    }
}
