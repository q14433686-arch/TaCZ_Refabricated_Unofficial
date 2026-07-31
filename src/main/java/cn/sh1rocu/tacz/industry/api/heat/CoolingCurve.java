package cn.sh1rocu.tacz.industry.api.heat;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 冷却曲线（A-2"工件离炉后按环境梯度降温"的数据驱动表达）。
 *
 * <p><b>为什么是注册表而非常数：</b>A-3 淬火槽区分水/油等介质，不同介质=不同冷却曲线；
 * 曲线本体用阶梯表描述：<code>[{above:600, loss_per_tick:2.0}, {above:300, loss_per_tick:1.0}, ...]</code>，
 * 温度高于 above 时每 tick 损温 loss_per_tick。程序按档结算，O(档数) 完成任意时长冷却——
 * 呼应 21 章性能规范"tick 级查表，非逐 tick 计算"（方块实体批量按 5 tick 结算一次）。</p>
 */
public record CoolingCurve(Identifier id, int ambient, List<Step> steps) {

    /**
     * 单档散热速率。
     *
     * @param above       温度高于此值时本档生效（档位按 above 降序排列使用）
     * @param lossPerTick 该档每 tick 散失的炉温单位（可小数，规则层内部以浮点累计后取整）
     */
    public record Step(int above, float lossPerTick) {
    }

    public static CoolingCurve fromJson(Identifier id, JsonObject json) {
        int ambient = GsonHelper.getAsInt(json, "ambient", HeatUnits.AMBIENT);
        List<Step> steps = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "steps").forEach(e -> {
            JsonObject o = e.getAsJsonObject();
            steps.add(new Step(GsonHelper.getAsInt(o, "above"), GsonHelper.getAsFloat(o, "loss_per_tick")));
        });
        // 强制按 above 降序：数据包顺序写错也不掀表（单条文件级防御）
        steps.sort((a, b) -> Integer.compare(b.above(), a.above()));
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("cooling curve " + id + " 的 steps 不能为空");
        }
        return new CoolingCurve(id, ambient, List.copyOf(steps));
    }
}
