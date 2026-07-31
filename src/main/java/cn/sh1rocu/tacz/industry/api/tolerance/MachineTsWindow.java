package cn.sh1rocu.tacz.industry.api.tolerance;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * 机器基础 TS 窗口（A-8a 表条目）：某类机器产出零件的 TS 散布区间。
 *
 * <p>同一"零件"在不同机器上产出的基础 TS 不同（手摇 25–45 → T5 铣削 80–95），
 * 窗口内具体取值由规则层用种子均匀抽样。{@code stabilityKind} 声明本机吃哪一种
 * "稳定性加成"来源（手摇=小游戏评分，动力=能量稳定度）——来源语义由规则层消费。</p>
 */
public record MachineTsWindow(Identifier id, int minTs, int maxTs, StabilityKind stabilityKind) {

    public enum StabilityKind {
        /** 手摇/手动：稳定性来自玩家节奏小游戏评分（0–1） */
        SKILL_MINIGAME("skill_minigame"),
        /** 动力机器（T3+）：稳定性来自 RPM 波动滑动平均（0–1） */
        POWER_STABILITY("power_stability"),
        /** 无稳定性项（加成按 0 计） */
        NONE("none");

        private final String serializedName;

        StabilityKind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public static StabilityKind fromString(String raw) {
            if (raw != null) {
                String key = raw.trim().toLowerCase().replace('-', '_');
                for (StabilityKind k : values()) {
                    if (k.serializedName.equals(key)) {
                        return k;
                    }
                }
            }
            return NONE;
        }
    }

    public static MachineTsWindow fromJson(Identifier id, JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min_ts");
        int max = GsonHelper.getAsInt(json, "max_ts");
        if (min < 0 || max > 100 || min > max) {
            throw new IllegalArgumentException("machine_ts " + id + " 窗口非法: [" + min + "," + max + "]");
        }
        StabilityKind kind = StabilityKind.fromString(GsonHelper.getAsString(json, "stability_kind", "none"));
        return new MachineTsWindow(id, min, max, kind);
    }
}
