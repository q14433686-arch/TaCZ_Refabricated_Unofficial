package com.tacz.guns.api.item.cartridge;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 口径类型管理器。
 * <p>
 * 数据驱动的注册表：通过 {@link Identifier} 唯一标识口径类型，
 * 支持运行时动态注册，后续可通过 JSON 数据包或 API 持续新增口径。
 * <p>
 * 设计模式类似 TACZ 的 {@link com.tacz.guns.resource.CommonAssetsManager}，
 * 但专注于口径/弹药规格的物理兼容性管理。
 * <p>
 * 使用方式：
 * <pre>
 * // 注册自定义口径
 * CartridgeTypeManager.register(
 *     Identifier.fromNamespaceAndPath("mymod", "338_lapua"),
 *     new CartridgeType("338 Lapua Magnum", 8.59f, 69.20f, 93.72f, ...)
 * );
 *
 * // 查询口径
 * Optional<CartridgeType> type = CartridgeTypeManager.getCartridgeType(
 *     Identifier.fromNamespaceAndPath("tacz", "9mm")
 * );
 *
 * // 兼容性检查
 * boolean compatible = CartridgeTypeManager.isCompatible(
 *     Identifier.fromNamespaceAndPath("tacz", "556_nato"),
 *     Identifier.fromNamespaceAndPath("tacz", "223_rem")
 * );
 * </pre>
 */
public final class CartridgeTypeManager {
    /** 口径类型注册表 */
    private static final Map<Identifier, CartridgeType> REGISTRY = new ConcurrentHashMap<>();

    /** 口径兼容性映射（双向兼容） */
    private static final Map<Identifier, Set<Identifier>> COMPATIBILITY_MAP = new ConcurrentHashMap<>();

    private CartridgeTypeManager() {}

    // ====== 注册 ======

    /**
     * 注册一个新的口径类型。
     *
     * @param id   口径唯一标识符（如 tacz:9mm）
     * @param type 口径规格数据
     * @throws IllegalArgumentException 如果 id 已被注册
     */
    public static void register(Identifier id, CartridgeType type) {
        Objects.requireNonNull(id, "Cartridge type ID must not be null");
        Objects.requireNonNull(type, "Cartridge type must not be null");
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Cartridge type already registered: " + id);
        }
        REGISTRY.put(id, type);
    }

    /**
     * 安全注册：如果 id 已存在则静默跳过。
     * 适用于数据包热重载场景。
     */
    public static void registerIfAbsent(Identifier id, CartridgeType type) {
        Objects.requireNonNull(id, "Cartridge type ID must not be null");
        Objects.requireNonNull(type, "Cartridge type must not be null");
        REGISTRY.putIfAbsent(id, type);
    }

    // ====== 查询 ======

    /**
     * 获取口径类型。
     *
     * @param id 口径唯一标识符
     * @return 对应的口径类型，不存在则返回 empty
     */
    public static Optional<CartridgeType> getCartridgeType(Identifier id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /**
     * 获取所有已注册的口径类型。
     */
    public static Set<Map.Entry<Identifier, CartridgeType>> getAllCartridgeTypes() {
        return Collections.unmodifiableSet(REGISTRY.entrySet());
    }

    /**
     * 检查口径 ID 是否已注册。
     */
    public static boolean isRegistered(Identifier id) {
        return REGISTRY.containsKey(id);
    }

    /**
     * 获取口径的显示名称。
     * 如果未注册，返回 ID 的字符串表示。
     */
    public static String getDisplayName(Identifier id) {
        CartridgeType type = REGISTRY.get(id);
        return type != null ? type.displayName() : id.toString();
    }

    // ====== 兼容性 ======

    /**
     * 注册口径兼容性对（双向兼容）。
     * <p>
     * 例如 .223 Remington 与 5.56 NATO 可以互相通用，
     * 但 .223 枪管打 5.56 有膛压风险。
     *
     * @param idA 口径 A
     * @param idB 口径 B
     */
    public static void registerCompatibility(Identifier idA, Identifier idB) {
        COMPATIBILITY_MAP.computeIfAbsent(idA, k -> ConcurrentHashMap.newKeySet()).add(idB);
        COMPATIBILITY_MAP.computeIfAbsent(idB, k -> ConcurrentHashMap.newKeySet()).add(idA);
    }

    /**
     * 判断两个口径是否兼容。
     * <p>
     * 兼容性判断逻辑：
     * <ol>
     *   <li>如果 idA == idB，直接兼容</li>
     *   <li>如果显式注册了兼容性对，兼容</li>
     *   <li>如果两个口径的物理尺寸完全匹配，兼容</li>
     * </ol>
     *
     * @param idA 枪膛口径
     * @param idB 弹药口径
     * @return 是否兼容
     */
    public static boolean isCompatible(@Nullable Identifier idA, @Nullable Identifier idB) {
        if (idA == null || idB == null) return false;
        if (idA.equals(idB)) return true;

        // 显式兼容性注册
        Set<Identifier> compatSet = COMPATIBILITY_MAP.get(idA);
        if (compatSet != null && compatSet.contains(idB)) return true;

        // 物理尺寸兼容
        CartridgeType typeA = REGISTRY.get(idA);
        CartridgeType typeB = REGISTRY.get(idB);
        if (typeA != null && typeB != null) {
            return typeA.isDimensionallyCompatibleWith(typeB);
        }

        return false;
    }

    /**
     * 判断弹药口径是否可安全装入枪膛口径。
     * <p>
     * 与 {@link #isCompatible} 不同，此方法考虑安全性：
     * 即使物理尺寸兼容，如果弹药口径的最大安全膛压超过枪膛口径的最大安全膛压，
     * 也不安全（如 .223 枪管打 5.56 NATO）。
     * <p>
     * 注意：此方法仅检查口径规格层面的安全性，不涉及具体装药量。
     * 装药过量的安全性判定由 P4 弹道公式在开火时实时计算。
     *
     * @param chamberCartridge 枪膛口径
     * @param ammoCartridge    弹药口径
     * @return 是否安全兼容
     */
    public static boolean isSafeToLoad(Identifier chamberCartridge, Identifier ammoCartridge) {
        if (!isCompatible(chamberCartridge, ammoCartridge)) return false;

        CartridgeType chamber = REGISTRY.get(chamberCartridge);
        CartridgeType ammo = REGISTRY.get(ammoCartridge);

        // 如果口径完全相同，安全
        if (chamberCartridge.equals(ammoCartridge)) return true;

        // 如果口径不同但兼容，检查膛压是否安全
        // 弹药口径的最大安全膛压不能超过枪膛口径的最大安全膛压的 105%
        if (chamber != null && ammo != null) {
            return ammo.maxSafePressure() <= chamber.maxSafePressure() * 1.05f;
        }

        return false;
    }

    // ====== 初始化 ======

    /**
     * 注册内置口径类型。
     * 在 {@link com.tacz.guns.GunMod#setup()} 中调用。
     */
    public static void init() {
        // 注册内置口径
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "9mm"), CartridgeType.NINE_MM);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "556_nato"), CartridgeType.FIVE_FIVE_SIX);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "762x39"), CartridgeType.SEVEN_SIX_TWO_39);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "762x51"), CartridgeType.SEVEN_SIX_TWO_51);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "45_acp"), CartridgeType.FORTY_FIVE_ACP);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "12_gauge"), CartridgeType.TWELVE_GAUGE);
        register(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "50_bmg"), CartridgeType.FIFTY_BMG);

        // 注册兼容性对
        // .223 Remington 与 5.56 NATO 物理兼容（但 .223 枪管打 5.56 有膛压风险）
        // registerCompatibility(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "223_rem"),
        //     Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "556_nato"));
    }

    /**
     * 清除所有注册数据（用于数据包热重载）。
     * 注意：通常不应在运行时调用此方法。
     */
    public static void clearAll() {
        REGISTRY.clear();
        COMPATIBILITY_MAP.clear();
    }
}
