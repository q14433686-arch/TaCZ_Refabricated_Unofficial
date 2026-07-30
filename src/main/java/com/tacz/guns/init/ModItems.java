package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static void init() {
        GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, MODERN_KINETIC_GUN);
    }

    public static ModernKineticGunItem MODERN_KINETIC_GUN = register("modern_kinetic_gun", new ModernKineticGunItem(itemProps("modern_kinetic_gun")));

//    public static ThrowableItem M67 = register("m67", new ThrowableItem());

    public static Item AMMO = register("ammo", new AmmoItem(itemProps("ammo")));
    public static AttachmentItem ATTACHMENT = register("attachment", new AttachmentItem(itemProps("attachment")));

    public static GunSmithTableItem GUN_SMITH_TABLE = register("gun_smith_table", new DefaultTableItem(ModBlocks.GUN_SMITH_TABLE, blockItemProps("gun_smith_table")));
    public static GunSmithTableItem WORKBENCH_111 = register("workbench_a", new GunSmithTableItem(ModBlocks.WORKBENCH_111, blockItemProps("workbench_a")));
    public static GunSmithTableItem WORKBENCH_211 = register("workbench_b", new GunSmithTableItem(ModBlocks.WORKBENCH_211, blockItemProps("workbench_b")));
    public static GunSmithTableItem WORKBENCH_121 = register("workbench_c", new GunSmithTableItem(ModBlocks.WORKBENCH_121, blockItemProps("workbench_c")));

    // LRTactical 专用合成台物品（移植 LesRaisins Tactical Equipements 的 Smithing Table）
    public static GunSmithTableItem LRT_SMITH_TABLE = register("tactical_table", new GunSmithTableItem(ModBlocks.LRT_SMITH_TABLE, blockItemProps("tactical_table")));


    public static Item TARGET = register("target", new BlockItem(ModBlocks.TARGET, blockItemProps("target")));
    public static Item STATUE = register("statue", new BlockItem(ModBlocks.STATUE, blockItemProps("statue")));
    public static Item AMMO_BOX = register("ammo_box", new AmmoBoxItem(itemProps("ammo_box")));
    public static Item TARGET_MINECART = register("target_minecart", new TargetMinecartItem(itemProps("target_minecart")));

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name));
    }

    private static Item.Properties itemProps(String name) {
        return new Item.Properties().setId(itemKey(name));
    }

    /**
     * 方块物品（{@link BlockItem} 及其子类）专用的 Properties。
     *
     * <h2>为什么必须显式调用 {@code useBlockDescriptionPrefix()}</h2>
     * 26.2 之前，{@code BlockItem} 自己覆写了 {@code getDescriptionId()}，
     * 直接返回所属方块的 {@code block.<ns>.<name>}，所以注册时什么都不用做。
     *
     * <p>26.2 把这套机制改成了「<b>在 Properties 上声明</b>」：
     * <ul>
     *   <li>{@code BlockItem} <b>不再覆写</b> {@code getDescriptionId()}（字节码确认，
     *       该类里已没有这个方法），统一继承 {@code Item#getDescriptionId}，
     *       返回构造时算好的 {@code descriptionId} 字段；</li>
     *   <li>该字段来自 {@code Properties#effectiveDescriptionId()}，
     *       其前缀由 {@code Properties.descriptionId} 这个 {@code DependantName} 决定，
     *       <b>默认是 {@code ITEM_DESCRIPTION_ID}</b>（即 {@code item.} 前缀）；</li>
     *   <li>要拿到 {@code block.} 前缀，必须显式调用
     *       {@code useBlockDescriptionPrefix()} 把它换成 {@code BLOCK_DESCRIPTION_ID}。</li>
     * </ul>
     * vanilla 自己的 {@code Items#registerBlock} 正是这么做的（字节码确认）。
     *
     * <p>移植时沿用了旧写法（只 {@code setId}），于是标靶与石像的名字变成了
     * {@code item.tacz.target} / {@code item.tacz.statue} —— 语言文件里只有
     * {@code block.tacz.target} / {@code block.tacz.statue}，键对不上就直接显示原始键名。
     * 这与上游语言文件一致（上游同样只有 {@code block.} 那一份），
     * 因此正确修法是让代码去适配 26.2 的新约定，而不是去改语言文件。
     *
     * <p>枪械工作台与三个工作台同为 {@code BlockItem} 子类，一并改用本方法：
     * {@code gun_smith_table} 的 {@code block.} 键本就存在（此前同样显示错误），
     * 三个 workbench 上下游都没有对应键（属枪包/上游自身缺失，不在本轮范围）。
     */
    private static Item.Properties blockItemProps(String name) {
        return new Item.Properties().setId(itemKey(name)).useBlockDescriptionPrefix();
    }

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), item);
    }
}