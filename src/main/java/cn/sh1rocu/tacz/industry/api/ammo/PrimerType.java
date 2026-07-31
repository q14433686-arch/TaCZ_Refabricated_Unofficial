package cn.sh1rocu.tacz.industry.api.ammo;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 底火结构类型（B 章 B-4）。仅表达退壳/复装体系差异，
 * 与是否腐蚀无关（腐蚀性由 LoadedRound.corrosivePrimer 独立记录）。
 */
public enum PrimerType {
    /** Boxer：自带火台，可手工退装复装 */
    BOXER("boxer", true),
    /** Berdan：火台与弹壳联体，实际不可复装 */
    BERDAN("berdan", false);

    public static final Codec<PrimerType> CODEC = IndustryCodecs.enumByName(PrimerType.class, values(), PrimerType::getSerializedName);

    private final String serializedName;
    private final boolean reloadServiceable;

    PrimerType(String serializedName, boolean reloadServiceable) {
        this.serializedName = serializedName;
        this.reloadServiceable = reloadServiceable;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public boolean isReloadServiceable() {
        return reloadServiceable;
    }
}
