package cn.sh1rocu.tacz.industry.api.cartridge;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 弹壳底缘结构类型。
 * 影响供弹机构适配（转轮偏好凸缘、弹链偏好无缘）与 N 章 Rim Lock 故障种子数据。
 * 仅作物理结构元数据，不含任何现实尺寸。
 */
public enum RimType {
    /** 无缘（现代自动手枪/步枪主流） */
    RIMLESS("rimless"),
    /** 凸缘（转轮、早期杠杆、霰弹） */
    RIMMED("rimmed"),
    /** 半凸缘 */
    SEMI_RIMMED("semi_rimmed"),
    /** 带式底缘（大口径全威力弹常见） */
    BELTED("belted"),
    /** 缩缘 */
    REBATED("rebated");

    public static final Codec<RimType> CODEC = IndustryCodecs.enumByName(RimType.class, values(), RimType::getSerializedName);

    private final String serializedName;

    RimType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }
}
