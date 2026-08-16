package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.item.ThrowableItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * 物品注册。
 *
 * <h2>与 NeoForge 版的差异</h2>
 * NeoForge 用 {@code DeferredRegister} 延迟注册；Fabric 直接 {@code Registry.register}。
 * 写法沿用本仓库 {@code com.tacz.guns.init.ModItems} 的既有模式，保持全仓一致。
 *
 * <p><b>26.2 硬性要求</b>：{@code Item.Properties} 必须通过 {@code setId(ResourceKey)}
 * 带上注册键，否则注册期直接报错。故此处提供 {@link #itemProps(String)} helper，
 * 与 TACZ 侧同名方法作用相同。
 *
 * <h2>当前注册状态（2026-08-12 复核）</h2>
 * {@code throwable / melee / consumable / detonator} 均已注册并有对应逻辑；
 * 旧注释仍停留在“只注册 THROWABLE”的早期步骤，已经过时。上游五个基础物品中
 * 只剩 {@code flash_shield} 尚未移植，因此没有预注册一个不可用空壳。
 */
public final class ModItems {
    public static final ThrowableItem THROWABLE =
            register("throwable", new ThrowableItem(itemProps("throwable")));

    public static final me.xjqsh.lrtactical.item.MeleeItem MELEE =
            register("melee", new me.xjqsh.lrtactical.item.MeleeItem(itemProps("melee")));

    public static final me.xjqsh.lrtactical.item.DetonatorItem DETONATOR =
            register("detonator", new me.xjqsh.lrtactical.item.DetonatorItem(itemProps("detonator")));

    public static final me.xjqsh.lrtactical.item.ConsumableItem CONSUMABLE =
            register("consumable", new me.xjqsh.lrtactical.item.ConsumableItem(itemProps("consumable")));

    private ModItems() {
    }

    public static void init() {
        // 触发静态初始化，完成上面的注册
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, name));
    }

    private static Item.Properties itemProps(String name) {
        return new Item.Properties().setId(itemKey(name));
    }

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, name), item);
    }
}
