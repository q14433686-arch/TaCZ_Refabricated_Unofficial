package cn.sh1rocu.tacz.industry.api.bullet;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 弹头质量档。C 章缠距匹配表的输入维度（快缠距管配重弹头）。
 */
public enum BulletMassClass {
    LIGHT("light"),
    STD("std"),
    HEAVY("heavy");

    public static final Codec<BulletMassClass> CODEC = IndustryCodecs.enumByName(BulletMassClass.class, values(), BulletMassClass::getSerializedName);

    private final String serializedName;

    BulletMassClass(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }
}
