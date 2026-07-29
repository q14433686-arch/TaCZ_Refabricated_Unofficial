package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 「可投掷物类型」与「近战武器类型」的类型注册表。
 *
 * <h2>为什么不用原版 {@code Registry}（与上游/NeoForge 版的关键差异）</h2>
 * NeoForge 版靠 {@code NewRegistryEvent} + {@code RegistryBuilder} 新建两个
 * <b>真正的原版注册表</b>。这条路在 Fabric 26.2 上<b>走不通</b>，字节码确认：
 *
 * <ul>
 *   <li>{@code BuiltInRegistries#registerSimple(...)} 是 <b>private</b>；</li>
 *   <li>{@code BuiltInRegistries#createContents()} / {@code freeze()} 同为 private，
 *       原版会在 bootstrap 结束后<b>冻结</b>全部内置注册表；</li>
 *   <li>{@code BuiltInRegistries.WRITABLE_REGISTRY}（根注册表的可写视图）
 *       <b>非 public</b>，模组拿不到。</li>
 * </ul>
 *
 * 也就是说，要在 Fabric 上凭空插一个新的原版注册表，只能靠 mixin 撬开
 * private 成员、或依赖 Fabric API 的注册表构建设施 —— 前者脆弱，后者是额外依赖。
 *
 * <h2>为什么普通 Map 就够了</h2>
 * 逐一核对了这两个注册表在<b>全仓的实际用法</b>，结论是：
 * 它们<b>只被当作「按 id 取类型」的查表</b>用（见 {@code MeleeIndexManager} 与
 * {@code ThrowableIndexManager}，各一处 {@code .get(id)}）。
 *
 * <p>没有任何地方用到原版注册表才有的能力 ——
 * 不参与网络同步、不需要数值 id、不需要 {@code Holder} / tag、不进数据包。
 * 因此一个 {@link Map} 完全等价，且<b>没有冻结时机问题</b>，
 * 也不必与原版 bootstrap 的生命周期赛跑。
 *
 * <p>这是刻意的「降级」：<b>用最小的机制满足真实需求</b>，
 * 而不是为了形式上对齐上游而引入 mixin 风险。
 * 若将来确有需要（例如类型要随数据包同步给客户端），再升级为真正的注册表不迟。
 */
public final class ModRegistries {
    /** 可投掷物类型：{@code lrtactical:grenade} 等。 */
    private static final Map<Identifier, Object> THROWABLE_TYPES = new LinkedHashMap<>();
    /** 近战武器类型：{@code lrtactical:normal} 等。 */
    private static final Map<Identifier, Object> MELEE_WEAPON_TYPES = new LinkedHashMap<>();

    private ModRegistries() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, path);
    }

    // ---------------- throwable ----------------

    /**
     * @return 传入的 type 本身，便于 {@code static final X FOO = registerThrowable("foo", new X());} 写法
     */
    public static <T> T registerThrowableType(Identifier id, T type) {
        Object old = THROWABLE_TYPES.putIfAbsent(id, type);
        if (old != null) {
            throw new IllegalStateException("Duplicate throwable type: " + id);
        }
        return type;
    }

    @Nullable
    public static Object getThrowableType(Identifier id) {
        return THROWABLE_TYPES.get(id);
    }

    public static Set<Identifier> throwableTypeIds() {
        return Collections.unmodifiableSet(THROWABLE_TYPES.keySet());
    }

    // ---------------- melee ----------------

    public static <T> T registerMeleeType(Identifier id, T type) {
        Object old = MELEE_WEAPON_TYPES.putIfAbsent(id, type);
        if (old != null) {
            throw new IllegalStateException("Duplicate melee weapon type: " + id);
        }
        return type;
    }

    @Nullable
    public static Object getMeleeType(Identifier id) {
        return MELEE_WEAPON_TYPES.get(id);
    }

    public static Set<Identifier> meleeTypeIds() {
        return Collections.unmodifiableSet(MELEE_WEAPON_TYPES.keySet());
    }
}
