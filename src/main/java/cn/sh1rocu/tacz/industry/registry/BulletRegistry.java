package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.bullet.BulletMassClass;
import cn.sh1rocu.tacz.industry.api.bullet.BulletType;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BulletType 数据驱动注册表（与 CartridgeRegistry 同构）。
 * 弹头类型 = 终点弹道效果档案；混装弹药里每发 LoadedRound 各带自己的 bulletType 引用。
 */
public final class BulletRegistry {
    private static final Map<Identifier, BulletType> REGISTRY = new LinkedHashMap<>();
    private static final Map<Identifier, BulletType> DEFAULTS = new LinkedHashMap<>();
    private static boolean initialized = false;

    private BulletRegistry() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // 内置六大基础弹头（B-6 表），数值以平衡 JSON 为最终权威，此处为可运行兜底
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "fmj"),
                BulletMassClass.STD, 1.0f, 0.0f, 0, 0, false, 0.0f));
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "hp"),
                BulletMassClass.STD, 1.0f, -0.2f, -1, 0, false, 0.25f));
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "ap"),
                BulletMassClass.STD, 0.9f, 0.4f, 1, 0, false, 0.0f));
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "tracer"),
                BulletMassClass.STD, 0.95f, -0.1f, 0, 4, false, 0.0f));
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "subsonic"),
                BulletMassClass.HEAVY, 0.85f, -0.1f, 0, 0, true, 0.0f));
        registerDefault(new BulletType(Identifier.fromNamespaceAndPath("taczind", "lrn"),
                BulletMassClass.STD, 1.0f, -0.4f, -1, 0, false, 0.1f));
        rebuild(Collections.emptyMap());
    }

    private static void registerDefault(BulletType type) {
        DEFAULTS.put(type.id(), type);
    }

    static synchronized void rebuild(Map<Identifier, BulletType> datapackEntries) {
        REGISTRY.clear();
        REGISTRY.putAll(DEFAULTS);
        REGISTRY.putAll(datapackEntries);
        GunMod.LOGGER.info("[taczind] BulletRegistry rebuilt: {} defaults + {} datapack entries = {} total",
                DEFAULTS.size(), datapackEntries.size(), REGISTRY.size());
    }

    @Nullable
    public static BulletType get(@Nullable Identifier id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /**
     * 未注册弹头兜底为 FMJ——不阻断玩法，但打 error 日志提示数据包缺漏。
     */
    public static BulletType getOrDefault(Identifier id) {
        BulletType type = get(id);
        if (type != null) {
            return type;
        }
        BulletType fmj = get(BulletType.defaultId());
        GunMod.LOGGER.error("[taczind] Unknown bullet type {}, falling back to FMJ", id);
        return fmj;
    }

    public static Collection<BulletType> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
