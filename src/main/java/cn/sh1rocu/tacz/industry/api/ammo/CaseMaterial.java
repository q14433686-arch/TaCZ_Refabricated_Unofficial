package cn.sh1rocu.tacz.industry.api.ammo;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 弹壳材质（B 章 B-3）：决定复装寿命、锈蚀行为与经济性。
 */
public enum CaseMaterial {
    /** 黄铜：可复装（默认上限 6 次）、不锈 */
    BRASS("brass", true, 6, false),
    /** 钢：廉价不可复装、漆层破损会锈 */
    STEEL("steel", false, 0, true),
    /** 铝：轻量不可复装、禁超装 */
    ALUMINUM("aluminum", false, 0, false);

    public static final Codec<CaseMaterial> CODEC = IndustryCodecs.enumByName(CaseMaterial.class, values(), CaseMaterial::getSerializedName);

    private final String serializedName;
    private final boolean reloadable;
    private final int maxReloadCount;
    private final boolean rustProne;

    CaseMaterial(String serializedName, boolean reloadable, int maxReloadCount, boolean rustProne) {
        this.serializedName = serializedName;
        this.reloadable = reloadable;
        this.maxReloadCount = maxReloadCount;
        this.rustProne = rustProne;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public int getMaxReloadCount() {
        return maxReloadCount;
    }

    public boolean isRustProne() {
        return rustProne;
    }
}
