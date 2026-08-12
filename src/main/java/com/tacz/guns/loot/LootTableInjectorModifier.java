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
 * 枪械战利品注入：把枪包定义的 {@code loot_injection} 追加到原版战利品表产出。
 *
 * <p>26.1.2 的战利品表属于 {@code RELOADABLE} registry layer，不在
 * {@code MinecraftServer#registryAccess()} 中。最终实现从枪包已经声明的少量目标 ID
 * 出发，经 {@code server.reloadableRegistries().lookup()} 正向查表并用实例身份确认当前表，
 * 不再尝试从值反查 ID。解析失败时保持原版掉落，绝不影响方块掉落主流程。</p>
 *
 * <p>表实例到 ID 的缓存使用同步 {@link WeakHashMap}：数据包重载后旧表可被回收，
 * 非目标表也通过哨兵缓存，避免在热路径重复解析。</p>
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
     * 在 reloadable loot-table registry 中解析当前表的 ID。
     *
     * <p>{@link HolderLookup.Provider} 只提供 key → value 查询，不能由值反查 key；但枪包管理器
     * 本来就持有全部目标 ID。这里只遍历这些通常为个位数的候选，正向取表并比较实例，
     * 避免全注册表 O(n) 扫描，也避免错误访问不含 loot table 的静态 registry layer。</p>
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