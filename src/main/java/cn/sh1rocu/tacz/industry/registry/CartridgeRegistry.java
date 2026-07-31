package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.cartridge.CartridgeType;
import cn.sh1rocu.tacz.industry.api.cartridge.PressureClass;
import cn.sh1rocu.tacz.industry.api.cartridge.RimType;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CartridgeType 数据驱动注册表（任务要求 1）。
 *
 * <p><b>为什么不用硬编码枚举：</b>口径会持续新增（社区枪包每加一把枪就可能加
 * 一种新口径），做成"类物品注册"的注册表才能零代码扩展。</p>
 *
 * <p><b>两层来源，同名覆盖：</b></p>
 * <ol>
 *   <li>代码内置默认（{@link #registerDefaults()}）——保证无数据包也可用，按 TACZ
 *       默认枪包的实际弹药 id 打底（旧枪包零迁移承诺的兑现）</li>
 *   <li>数据包 JSON（data/&lt;ns&gt;/cartridge/&lt;name&gt;.json，由
 *       {@link cn.sh1rocu.tacz.industry.loader.IndustryDataLoader} 重载合并）——
 *       id 相同即覆盖内置默认，让整合包可调校</li>
 * </ol>
 *
 * <p>线程与生命周期：仅服务端数据重载时整体重建，运行期只读。不按数据包同步
 * （数据组件只存 Identifier 引用，两端各自查表——同枪包两端对齐即可；错配时
 * 查表失败按"unknown"口径处理，不崩档）。</p>
 */
public final class CartridgeRegistry {
    private static final Map<Identifier, CartridgeType> REGISTRY = new LinkedHashMap<>();
    private static final Map<Identifier, CartridgeType> DEFAULTS = new LinkedHashMap<>();
    private static boolean defaultsRegistered = false;

    private CartridgeRegistry() {
    }

    /**
     * 模块初始化：注册代码内置默认口径。
     */
    public static synchronized void init() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;
        registerDefaults();
        rebuild();
    }

    private static void registerDefaults() {
        // 与 TACZ 默认枪包弹药 id 对齐（默认枪包命名空间 tacz）
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "762x39"), RimType.RIMLESS, PressureClass.MEDIUM, 38.7f, 7.62f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "556x45"), RimType.RIMLESS, PressureClass.HIGH, 44.7f, 5.56f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "762x51"), RimType.RIMLESS, PressureClass.HIGH, 51.2f, 7.62f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "9x19"), RimType.RIMLESS, PressureClass.LOW, 19.2f, 9.0f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "45acp"), RimType.RIMLESS, PressureClass.LOW, 22.8f, 11.4f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "12g"), RimType.RIMMED, PressureClass.LOW, 70.0f, 18.5f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "308"), RimType.RIMLESS, PressureClass.HIGH, 51.2f, 7.62f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "762x54r"), RimType.RIMMED, PressureClass.HIGH, 53.7f, 7.62f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "357mag"), RimType.RIMMED, PressureClass.MAGNUM, 33.0f, 9.1f);
        defaultOf(Identifier.fromNamespaceAndPath("tacz", "50bmg"), RimType.RIMMED, PressureClass.MAGNUM, 99.0f, 12.7f);
        // 工业时代早期弹药（本模组自行内容）
        defaultOf(Identifier.fromNamespaceAndPath("taczind", "44_henry_rimfire"), RimType.RIMMED, PressureClass.LOW, 23.6f, 11.0f);
        defaultOf(Identifier.fromNamespaceAndPath("taczind", "577_450_martini"), RimType.RIMMED, PressureClass.MEDIUM, 66.0f, 11.6f);
    }

    private static void defaultOf(Identifier id, RimType rim, PressureClass pressure, float caseLength, float diameter) {
        DEFAULTS.put(id, new CartridgeType(id, rim, pressure, caseLength, diameter));
    }

    /**
     * 数据包重载时整体重建：默认打底 + JSON 覆盖。
     */
    static synchronized void rebuild(Map<Identifier, CartridgeType> datapackEntries) {
        REGISTRY.clear();
        REGISTRY.putAll(DEFAULTS);
        REGISTRY.putAll(datapackEntries);
        GunMod.LOGGER.info("[taczind] CartridgeRegistry rebuilt: {} defaults + {} datapack entries = {} total",
                DEFAULTS.size(), datapackEntries.size(), REGISTRY.size());
    }

    private static void rebuild() {
        rebuild(Collections.emptyMap());
    }

    /**
     * 查表。未注册 id 返回 null —— 调用方必须走 {@link #getOrUnknown} 或自行判空。
     */
    @Nullable
    public static CartridgeType get(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return REGISTRY.get(id);
    }

    /**
     * 永不为 null 的兜底查询：未知口径映射到 taczind:unknown 占位（保住存档与网络同步
     * 不因缺数据包而崩，玩法层对 unknown 一律判不兼容）。
     */
    public static CartridgeType getOrUnknown(Identifier id) {
        CartridgeType type = get(id);
        if (type != null) {
            return type;
        }
        return CartridgeType.placeholder(Identifier.fromNamespaceAndPath("taczind", "unknown"));
    }

    public static boolean contains(Identifier id) {
        return REGISTRY.containsKey(id);
    }

    public static Collection<CartridgeType> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 从数据包 JSON 解析（由 IndustryDataLoader 调用）。
     */
    public static CartridgeType fromJson(Identifier id, JsonObject json) {
        return CartridgeType.fromJson(id, json);
    }
}
