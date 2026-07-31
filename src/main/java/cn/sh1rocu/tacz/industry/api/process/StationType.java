package cn.sh1rocu.tacz.industry.api.process;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 工位类型（工序的物理执行场所分类，A-2~A-5 各机器/工具方块在物品层落地时的映射键）。
 *
 * <p>手搓期（P1）开放 ANVIL / CRUCIBLE / QUENCH_TANK / HAND_TOOL 四席；
 * 后续阶段追加席位=新枚举值（定制机器方块绑定同名工位）。</p>
 */
public enum StationType {
    /** 锻砧：热度条节奏锻打 */
    ANVIL("anvil"),
    /** 坩埚炉：灌注/重熔除渣（密闭容器工序，非锤击型） */
    CRUCIBLE("crucible"),
    /** 淬火槽：急速冷却收尾 */
    QUENCH_TANK("quench_tank"),
    /** 手摇工具类：低精度、无热需求的冷加工 */
    HAND_TOOL("hand_tool");

    private final String serializedName;

    StationType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    @Nullable
    public static StationType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase().replace('-', '_');
        for (StationType s : values()) {
            if (s.serializedName.equals(key)) {
                return s;
            }
        }
        return null;
    }

    /** JSON 便捷读法：缺省 ANVIL（数据包作者最常写的工位）。 */
    public static StationType fromJson(JsonObject json, String key) {
        StationType s = fromString(GsonHelper.getAsString(json, key, ANVIL.serializedName));
        if (s == null) {
            throw new IllegalArgumentException("station 非法: " + GsonHelper.getAsString(json, key));
        }
        return s;
    }
}
