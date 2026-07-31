package cn.sh1rocu.tacz.industry.api.tolerance;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * TS 分级带（A-8d 表条目）：整枪/零件 TS 落入该区间的属性映射。
 *
 * <p>五个键的语义一字排开，全部是玩法修正系数（乘数或偏差幅度），
 * 不绑定任何现实数值：</p>
 * <ul>
 *   <li>{@code inaccuracyMult} → C 章散布链（Q-04 注入点消费）</li>
 *   <li>{@code malfunctionMult} → E 章故障率基线倍率</li>
 *   <li>{@code durabilityMult} → I 章部件耐久上限倍率</li>
 *   <li>{@code velocityDeviation} → 初速个体偏差幅度 ±</li>
 * </ul>
 */
public record TsGrade(
        Identifier id,
        int minTs,
        int maxTs,
        String displayKey,
        float inaccuracyMult,
        float malfunctionMult,
        float durabilityMult,
        float velocityDeviation
) {
    public boolean contains(int ts) {
        return ts >= minTs && ts < maxTs;
    }

    public static TsGrade fromJson(Identifier id, JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min_ts");
        int max = GsonHelper.getAsInt(json, "max_ts");
        if (min < 0 || max > 101 || min >= max) {
            throw new IllegalArgumentException("grade_band " + id + " 区间非法: [" + min + "," + max + ")（max 允许 101 以封顶 100）");
        }
        return new TsGrade(
                id,
                min,
                max,
                GsonHelper.getAsString(json, "display_key", id.getPath()),
                GsonHelper.getAsFloat(json, "inaccuracy_mult", 1f),
                GsonHelper.getAsFloat(json, "malfunction_mult", 1f),
                GsonHelper.getAsFloat(json, "durability_mult", 1f),
                GsonHelper.getAsFloat(json, "velocity_deviation", 0.015f)
        );
    }
}
