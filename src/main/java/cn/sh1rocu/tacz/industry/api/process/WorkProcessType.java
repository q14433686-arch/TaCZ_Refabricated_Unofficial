package cn.sh1rocu.tacz.industry.api.process;

import cn.sh1rocu.tacz.industry.api.heat.HeatBand;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 工序类型（A-2 锻打工序的数据驱动定义；材料 → 零件/新材料 的带热度叙事处理单元）。
 *
 * <p><b>设计解释（A-2 关键玩法）：</b>锤击型工序用"次数×每锤耗温"刻画：
 * 工件从炉中取出时温度有限，锤击消耗温度，温度跌出工作带必须回炉——
 * 工序难度=炉次往返节奏的难度。坩埚型工序（非锤击）则 strikes=0、用 {@code processTicks}
 * 表示盛放时间。</p>
 *
 * @param id              注册名（数据包文件名）
 * @param station         工位
 * @param inputMaterial   入料（工件持有的 material 必须等于它才准入）
 * @param output          产出标识（材料 id 或零件 id——零件在物品层落地前允许悬空，
 *                        落地时由输出映射表挂到具体物品）
 * @param band            温度带（坩埚工序同样需要：重熔也有可塑窗口）
 * @param strikesRequired 锤击次数（0 = 非锤击型）
 * @param heatPerStrike   每锤消耗炉温单位
 * @param processTicks    非锤击工序的盛放时长（tick；锤击型可忽略）
 * @param qualityJitter   收锤质量随机抖动幅度 ±（quality_seed 掷骰）
 * @param idealFinishBonus 理想带收锤的额外质量加成（A-2 正反馈）
 */
public record WorkProcessType(
        Identifier id,
        StationType station,
        Identifier inputMaterial,
        Identifier output,
        HeatBand band,
        int strikesRequired,
        int heatPerStrike,
        long processTicks,
        float qualityJitter,
        float idealFinishBonus
) {
    public boolean isStrikeBased() {
        return strikesRequired > 0;
    }

    /** 干完一道工序的总热能预算（=耗温≠需升温；用于手册与平衡直观展示）。 */
    public int totalStrikeHeatCost() {
        return strikesRequired * heatPerStrike;
    }

    public static WorkProcessType fromJson(Identifier id, JsonObject json) {
        StationType station = StationType.fromJson(json, "station");
        Identifier input = Identifier.tryParse(GsonHelper.getAsString(json, "input_material"));
        Identifier output = Identifier.tryParse(GsonHelper.getAsString(json, "output"));
        if (input == null || output == null) {
            throw new IllegalArgumentException("process " + id + " 的 input_material/output 必须是合法资源名");
        }
        HeatBand band = HeatBand.fromJson(GsonHelper.getAsJsonObject(json, "heat_band"));
        int strikes = GsonHelper.getAsInt(json, "strikes_required", 0);
        int heatPer = GsonHelper.getAsInt(json, "heat_per_strike", 0);
        if (strikes < 0 || heatPer < 0 || (station == StationType.ANVIL && strikes <= 0)) {
            throw new IllegalArgumentException("process " + id + " 锤击参数非法（anvil 工序必须 strikes_required>0）: strikes="
                    + strikes + " heat_per_strike=" + heatPer);
        }
        long ticks = GsonHelper.getAsLong(json, "process_ticks", 0L);
        float jitter = GsonHelper.getAsFloat(json, "quality_jitter", 0.05f);
        float bonus = GsonHelper.getAsFloat(json, "ideal_finish_bonus", 0.0f);
        return new WorkProcessType(id, station, input, output, band, strikes, heatPer, ticks, jitter, bonus);
    }
}
