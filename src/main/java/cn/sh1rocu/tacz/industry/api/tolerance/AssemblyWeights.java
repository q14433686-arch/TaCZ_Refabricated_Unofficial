package cn.sh1rocu.tacz.industry.api.tolerance;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 装配加权表（A-8c：部件 TS → 整枪 TS 的权重方案）。
 *
 * <p>多套方案并存（如 default_gun 用 枪管0.35/枪机0.25/机匣0.15/簧系0.10/其它0.15），
 * 数据包可为不同枪族注册不同权重（霰弹枪的重枪管、冲锋枪的重枪机…）。
 * 权重归一化由规则层运行时完成（数据和不必恰为 1，写错不掀表）。</p>
 */
public record AssemblyWeights(Identifier id, Map<String, Float> weights) {

    public static AssemblyWeights fromJson(Identifier id, JsonObject json) {
        JsonObject obj = GsonHelper.getAsJsonObject(json, "weights");
        Map<String, Float> map = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            float w = GsonHelper.getAsFloat(obj, key, 0f);
            if (w < 0f) {
                throw new IllegalArgumentException("weights " + id + " 中 " + key + " 权重为负: " + w);
            }
            map.put(key, w);
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("weights " + id + " 的 weights 不能为空");
        }
        return new AssemblyWeights(id, Map.copyOf(map));
    }

    /** 权重总和（规则层归一化的分母；==0 视为坏表，调用方拒绝）。 */
    public float total() {
        float sum = 0f;
        for (float w : weights.values()) {
            sum += w;
        }
        return sum;
    }
}
