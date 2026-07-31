package cn.sh1rocu.tacz.industry.api.ammo;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;

/**
 * 弹壳状态（B 章 B-7 复装检查工位的输出）。
 * CRACKED/DEFORMED 弹壳在供弹具 tryLoad 规则中被拒装——见各 FeedDeviceData 嵌套规则。
 */
public enum CaseState {
    /** 新出厂/未击发 */
    FACTORY_NEW("factory_new", true),
    /** 击发后的空壳（未检） */
    FIRED_SPENT("fired_spent", true),
    /** 目检合格（可复装） */
    INSPECTED_OK("inspected_ok", true),
    /** 裂纹（拒装；混入将供 F 章膛压泄漏权重） */
    CRACKED("cracked", false),
    /** 变形（拒装） */
    DEFORMED("deformed", false);

    public static final Codec<CaseState> CODEC = IndustryCodecs.enumByName(CaseState.class, values(), CaseState::getSerializedName);

    private final String serializedName;
    /** 是否允许装入供弹具 */
    private final boolean loadable;

    CaseState(String serializedName, boolean loadable) {
        this.serializedName = serializedName;
        this.loadable = loadable;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public boolean isLoadable() {
        return loadable;
    }
}
