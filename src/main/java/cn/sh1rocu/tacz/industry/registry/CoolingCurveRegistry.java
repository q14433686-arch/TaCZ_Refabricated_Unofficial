package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.heat.CoolingCurve;
import cn.sh1rocu.tacz.industry.api.heat.HeatUnits;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CoolingCurve 注册表（A-2 冷却介质表；air/water/oil…）。
 *
 * <p><b>兜底策略：</b>查询未知曲线/空表时返回内置 air 常量曲线——
 * 冷却行为必须永远有定义（工件在任何世界状态下都不能"不冷却"），
 * 这是属于安全网的少数代码内置常量，数值仅作手感兜底：</p>
 * <ul><li>高于 600 档散 2.0/tick、600–300 档 1.0/tick、300 以下 0.5/tick（对应空气取 5°C 档的手感梯度）</li></ul>
 */
public final class CoolingCurveRegistry {
    private static final Map<Identifier, CoolingCurve> REGISTRY = new LinkedHashMap<>();

    /** 内置兜底曲线（仅当数据包全缺时使用；正常玩法下由 data/taczind/cooling_curve/air.json 覆盖同 id）。 */
    public static final Identifier AIR_ID = Identifier.fromNamespaceAndPath(IndustryIds.MOD_ID, "air");
    private static final CoolingCurve FALLBACK_AIR = new CoolingCurve(
            AIR_ID,
            HeatUnits.AMBIENT,
            List.of(
                    new CoolingCurve.Step(600, 2.0f),
                    new CoolingCurve.Step(300, 1.0f),
                    new CoolingCurve.Step(0, 0.5f)
            ));

    private CoolingCurveRegistry() {
    }

    public static synchronized void rebuild(Map<Identifier, CoolingCurve> datapackEntries) {
        REGISTRY.clear();
        REGISTRY.putAll(datapackEntries);
        GunMod.LOGGER.info("[taczind] CoolingCurveRegistry rebuilt: {} entries", REGISTRY.size());
    }

    public static synchronized void clear() {
        REGISTRY.clear();
    }

    @Nullable
    public static CoolingCurve get(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return REGISTRY.get(id);
    }

    /** 永不为 null 的查询：未知/缺失一律回退内置空气曲线。 */
    public static CoolingCurve getOrAir(@Nullable Identifier id) {
        CoolingCurve c = get(id);
        return c != null ? c : getOrDefaultAir();
    }

    /** 缺数据包态兜底。 */
    public static CoolingCurve getOrDefaultAir() {
        CoolingCurve c = REGISTRY.get(AIR_ID);
        return c != null ? c : FALLBACK_AIR;
    }

    /** 工具方法：把曲线解析为规则层直接可用的形式（未来联网同步/调试 dump 用）。 */
    public static String describe(CoolingCurve curve) {
        StringBuilder sb = new StringBuilder(curve.id().toString()).append(" [ambient=").append(curve.ambient()).append(']');
        for (CoolingCurve.Step s : curve.steps()) {
            sb.append(" >").append(s.above()).append(':').append(s.lossPerTick());
        }
        return sb.toString();
    }
}
