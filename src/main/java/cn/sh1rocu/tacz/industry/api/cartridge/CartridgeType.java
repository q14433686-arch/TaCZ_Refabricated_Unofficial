package cn.sh1rocu.tacz.industry.api.cartridge;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * 弹药口径类型（Cartridge Type）。
 *
 * <p><b>职责边界（与 BulletType 严格分工）：</b>
 * CartridgeType 只描述"这一弹种的物理口径规格"——能不能装进某把枪、某个供弹具；
 * 不描述命中效果。命中效果归 {@code BulletType}。</p>
 *
 * <p>数据驱动：实例来自数据包 JSON（data/&lt;ns&gt;/cartridge/&lt;name&gt;.json），
 * 由 {@code CartridgeRegistry} 持有，代码/数据包均可注册新口径。</p>
 *
 * @param id             注册名（如 taczind:762x39），由文件名决定，不在 JSON 内书写
 * @param rimType        底缘结构（供弹机构适配判定用）
 * @param pressureClass  膛压等级（F 章风险池与装药校验用，游戏分档）
 * @param caseLengthMm   弹壳长度的游戏参考值（供弹具几何兼容校验，未来弹匣井长度判定启用）
 * @param bulletDiameterMm 弹径游戏参考值
 */
public record CartridgeType(
        Identifier id,
        RimType rimType,
        PressureClass pressureClass,
        float caseLengthMm,
        float bulletDiameterMm
) {
    /**
     * 默认膛压等级 —— 旧 JSON 缺省时按中间威力弹处理，保证向后兼容。
     */
    public static CartridgeType fromJson(Identifier id, JsonObject json) {
        RimType rim = json.has("rim_type")
                ? RimType.valueOf(GsonHelper.getAsString(json, "rim_type").toUpperCase().replace('-', '_'))
                : RimType.RIMLESS;
        PressureClass pressure = json.has("pressure_class")
                ? PressureClass.valueOf(GsonHelper.getAsString(json, "pressure_class").toUpperCase())
                : PressureClass.MEDIUM;
        float caseLength = GsonHelper.getAsFloat(json, "case_length_mm", 39f);
        float diameter = GsonHelper.getAsFloat(json, "bullet_diameter_mm", 7.62f);
        return new CartridgeType(id, rim, pressure, caseLength, diameter);
    }

    /**
     * 占位口径（数据的弹药未能解析出口径时的兜底）."empty" 口径永远不会通过兼容校验。
     */
    public static CartridgeType placeholder(Identifier id) {
        return new CartridgeType(id, RimType.RIMLESS, PressureClass.MEDIUM, 0f, 0f);
    }
}
