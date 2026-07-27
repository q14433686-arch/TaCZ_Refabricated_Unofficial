package com.tacz.guns.loot;

import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 枪械战利品注入：把枪包定义的 {@code loot_injection} 追加到原版战利品表的产出里。
 *
 * <h2>第 39 轮：解除封印，并更正上一版 TODO 的判断</h2>
 * 旧 TODO 写的是「26.2 的战利品表由 HolderGetter/LootContext 解析，
 * <b>无法</b>从 {@code ServerLevel.registryAccess()} 取得，会在方块掉落时崩溃」。
 * 本轮逐个方法核对 26.2 字节码，结论是<b>这句话只对了一半</b>：
 * <ul>
 *   <li>{@code ServerLevel#registryAccess()} —— <b>确实已不存在</b>（近似只剩
 *       {@code recipeAccess}），照抄 1.21.1 的写法必然编译失败；</li>
 *   <li>但 {@code MinecraftServer#registryAccess()} <b>仍然存在</b>，返回
 *       {@code RegistryAccess$Frozen}；而 {@code RegistryAccess} 上
 *       {@code lookupOrThrow(ResourceKey)} → {@code Registry} 与
 *       {@code Registry#getKey(Object)} → {@code Identifier} 也都在。</li>
 * </ul>
 * 也就是说「{@code context.getLevel().getServer().registryAccess()}」这条链
 * <b>是通的</b> —— 崩溃的原因不是 API 没了，而是当年直接写了
 * {@code ServerLevel#registryAccess()}。反查 ID 这个思路本身可以继续用，
 * 不必像 TODO 说的那样搬到资源加载路径去重做。
 *
 * <h2>本轮实际修掉的两个问题</h2>
 * <ol>
 *   <li><b>{@code ID_CACHE} 内存泄漏。</b> 原来用 {@link HashMap} 且以
 *       {@code LootTable} 实例为<b>强引用</b>键。战利品表在每次
 *       {@code /reload}、切换存档、重载资源包时都会整体重建，
 *       旧表实例被这个 static map 一直攥着，永远不会被回收。
 *       改为 {@link WeakHashMap} + {@code synchronizedMap}：
 *       表实例一旦无人引用即可回收；加锁是因为战利品生成可能发生在
 *       服务端多个线程上。</li>
 *   <li><b>{@code getLevel()} 可能为 null。</b> {@code LootContext#getLevel()}
 *       实际是 {@code params.getLevel()}（字节码确认），而 {@code LootParams}
 *       的 {@code level} 字段由构造器直接写入，理论上非空；但战利品表也可能
 *       被数据生成 / 第三方 mod 在无世界的环境下驱动。这里加一道防御，
 *       任何一环拿不到就原样返回，绝不让注入逻辑把主流程带崩。</li>
 * </ol>
 */
public class LootTableInjectorModifier {
    /**
     * 战利品表 → 注册表 ID 的缓存。
     *
     * <p>用 {@link WeakHashMap} 而非 {@link HashMap}：键是 {@code LootTable} 实例，
     * 每次 {@code /reload} 都会换一批新实例，强引用会导致旧表永久驻留。</p>
     */
    private static final Map<LootTable, Identifier> ID_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 哨兵：表示「已经查过，确认这张表不是注入目标」。
     *
     * <p>用引用相等（{@code ==}）比较，不参与任何注册表查询。</p>
     */
    private static final Identifier NOT_A_TARGET = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "not_a_loot_target");

    public static @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context, LootTable table) {
        // 【第 40 轮】整体兜底。
        //
        // 本方法挂在 LootTable#getRandomItems 的返回值上，位于<b>方块掉落主干路径</b>上
        // （BlockBehaviour#getDrops -> Block#dropResources，甚至水流冲毁方块也会走到）。
        // 上一轮就是因为这里抛了 IllegalStateException，导致「挖方块 / 水流蔓延」直接崩服务端。
        //
        // 战利品注入是<b>锦上添花</b>的功能，绝不该让它把主流程带崩。
        // 因此这里无论内部出什么问题，都只记日志并原样返回原始掉落物。
        try {
            return doApplyUnsafe(generatedLoot, context, table);
        } catch (Exception e) {
            if (WARNED.compareAndSet(false, true)) {
                GunMod.LOGGER.error(
                        "TACZ loot injection failed; falling back to vanilla drops. "
                                + "This message is logged only once per session.", e);
            }
            return generatedLoot;
        }
    }

    /** 只在会话内报告一次注入失败，避免每次掉落都刷屏。 */
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private static @NotNull ObjectArrayList<ItemStack> doApplyUnsafe(ObjectArrayList<ItemStack> generatedLoot, LootContext context, LootTable table) {
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null) {
            return generatedLoot;
        }
        // 快速退出：枪包压根没声明任何注入目标时，不做任何解析（绝大多数存档的常态）。
        if (manager.getLootInjectionTargets().isEmpty()) {
            return generatedLoot;
        }

        // 注意不能直接用 computeIfAbsent + null：Map 约定「映射到 null」等同「不存在」，
        // 那样每一次非目标表的掉落都会重跑一遍 resolveId（候选集遍历），
        // 而绝大多数方块都不是注入目标 —— 等于把开销加在最热的路径上。
        // 这里用 NOT_A_TARGET 哨兵把「查过且确认不是目标」也缓存下来。
        Identifier lootTableId = ID_CACHE.computeIfAbsent(table, lootTable -> {
            Identifier resolved = resolveId(context, lootTable);
            return resolved == null ? NOT_A_TARGET : resolved;
        });
        if (lootTableId == NOT_A_TARGET) {
            return generatedLoot;
        }

        List<LootTableInjection> injections = manager.getLootTableInjections(lootTableId);
        if (injections.isEmpty()) {
            return generatedLoot;
        }

        for (LootTableInjection injection : injections) {
            generatedLoot.addAll(injection.createStacks(context));
        }
        return generatedLoot;
    }

    /**
     * 反查战利品表的注册 ID。
     *
     * <h2>第 40 轮：上一轮用 {@code server.registryAccess()} 是错的，已实测崩溃</h2>
     * 崩溃日志（挖方块 / 水流冲毁方块时必现）：
     * <pre>
     * IllegalStateException: Missing registry: ResourceKey[minecraft:root / minecraft:loot_table]
     *   at RegistryAccess.lookupOrThrow(RegistryAccess.java:22)
     *   at LootTableInjectorModifier.resolveId
     * </pre>
     *
     * <p>上一轮我只核对了「{@code MinecraftServer#registryAccess()} 这个方法存在」，
     * <b>没有核对「战利品表是否真的在这个 RegistryAccess 里」</b>—— 这是两回事。
     * 26.2 的注册表分层（{@code RegistryLayer} 枚举）为：
     * {@code STATIC} / {@code WORLDGEN} / {@code DIMENSIONS} / {@code RELOADABLE}。
     * {@code server.registryAccess()} 只覆盖前三层，
     * 而战利品表属于<b>随数据包重载</b>的 {@code RELOADABLE} 层，
     * 由独立的 {@code ReloadableServerRegistries.Holder} 持有
     * （{@code MinecraftServer#reloadableRegistries()}）。
     * 所以那条 TODO 说的「无法从 registryAccess 取得」<b>完全正确</b>，是我推翻错了。</p>
     *
     * <h2>为什么改成「正查」而不是继续反查</h2>
     * {@code reloadableRegistries().lookup()} 返回的是 {@link HolderLookup.Provider}，
     * 它只能按 key 取值，<b>没有</b>由值反查 key 的方法
     * （{@code HolderLookup} 上只有 {@code key()}，返回的是注册表自身的 key）。
     * 虽然可以用 {@code listElements()} 全表遍历来反查，但那是 O(n) 且要处理缓存失效。
     *
     * <p>更稳的做法是掉头：<b>我们本来就知道要注入哪些表</b>——
     * {@code LootInjectionManager} 的 {@code injections} 正是以「目标表 ID」为键的 Map。
     * 于是这里只需拿枪包声明过的那几个 ID（默认枪包只有 1 个：
     * {@code minecraft:chests/spawn_bonus_chest}）去正查，比对实例是否相同即可。
     * 候选集通常只有个位数，且完全避开了「注册表不存在」这类运行时假设。</p>
     *
     * @return 查不到时返回 {@code null}，调用方会原样跳过注入
     */
    @Nullable
    private static Identifier resolveId(LootContext context, LootTable lootTable) {
        ServerLevel level = context.getLevel();
        if (level == null) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null) {
            return null;
        }
        // 只在「枪包声明过要注入的表」里找，不做全注册表反查。
        Set<Identifier> candidates = manager.getLootInjectionTargets();
        if (candidates.isEmpty()) {
            return null;
        }
        HolderLookup.Provider lookup = server.reloadableRegistries().lookup();
        // 【r41】这里的泛型有两个坑，都已用 26.2 的<b>泛型签名</b>（不是描述符）核对：
        //
        // 1. Provider#lookup 的签名是
        //        <T> Optional<? extends RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>>)
        //    返回值是【协变】的 Optional<? extends ...>，不能赋给
        //    Optional<RegistryLookup<LootTable>> —— r40 就是在这里编译失败的。
        // 2. 即便改用 var，推断出的也是 RegistryLookup<capture of ? extends LootTable>，
        //    再拿它去 get(ResourceKey<LootTable>) 仍可能因捕获类型不匹配而报错。
        //
        // 因此改用 lookupOrThrow：它的签名是
        //        <T> RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>>)
        //    返回的是【不带通配符】的 RegistryLookup<T>，T 直接由参数推断为 LootTable，
        //    两个坑都绕开了。它在注册表缺失时抛 IllegalStateException，
        //    而本方法整体被 doApply 的 try-catch 兜住（见那里的说明），
        //    因此不会像 r40 那样把方块掉落带崩。
        HolderLookup.RegistryLookup<LootTable> registry = lookup.lookupOrThrow(Registries.LOOT_TABLE);
        for (Identifier candidate : candidates) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, candidate);
            LootTable value = registry.get(key).map(Holder::value).orElse(null);
            if (value == lootTable) {
                return candidate;
            }
        }
        return null;
    }
}