package cn.sh1rocu.tacz.industry.api.cartridge;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 弹药标准膛压等级（游戏化分档，仅用于 F 章炸膛权重与装药量上限校验，
 * 不对应任何现实压力数值）。
 */
public enum PressureClass {
    /** 低压（手枪弹/霰弹类） */
    LOW("low", 1.0f),
    /** 中压（中间威力弹） */
    MEDIUM("medium", 1.5f),
    /** 高压（全威力步枪弹） */
    HIGH("high", 2.0f),
    /** 强装药档（大口径/马格南类） */
    MAGNUM("magnum", 3.0f);

    public static final Codec<PressureClass> CODEC = IndustryCodecs.enumByName(PressureClass.class, values(), PressureClass::getSerializedName);

    private final String serializedName;
    /** 过压权重放大系数（游戏平衡值）：超装时风险池按此放大 */
    private final float overpressureWeight;

    PressureClass(String serializedName, float overpressureWeight) {
        this.serializedName = serializedName;
        this.overpressureWeight = overpressureWeight;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public float getOverpressureWeight() {
        return overpressureWeight;
    }
}
