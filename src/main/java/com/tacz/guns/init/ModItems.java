package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.*;
import com.tacz.guns.industry.item.IndustryTaggedItem;
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
    /** A configured, non-stackable physical detachable magazine. */
    public static Item MAGAZINE = register("magazine", new MagazineItem(itemProps("magazine")));
    /** Neutral Basin-formed shell; a matching firearm held by a Deployer calibrates its final magazine family. */
    public static Item MAGAZINE_BLANK = register("magazine_blank", new Item(itemProps("magazine_blank").stacksTo(16)));

    // Create Fly industrial-chain intermediates. These are deliberately native
    // TACZ items, while their processing recipes live in data/tacz/recipe/create.
    public static Item CARBON_DUST = register("carbon_dust", new Item(itemProps("carbon_dust")));
    public static Item SULFUR_DUST = register("sulfur_dust", new Item(itemProps("sulfur_dust")));
    public static Item CINNABAR_DUST = register("cinnabar_dust", new Item(itemProps("cinnabar_dust")));
    public static Item PIG_IRON_INGOT = register("pig_iron_ingot", new Item(itemProps("pig_iron_ingot")));
    public static Item HIGH_CARBON_STEEL_INGOT = register("high_carbon_steel_ingot", new Item(itemProps("high_carbon_steel_ingot")));
    public static Item HIGH_CARBON_STEEL_PLATE = register("high_carbon_steel_plate", new Item(itemProps("high_carbon_steel_plate")));
    public static Item INDUSTRIAL_PROPELLANT = register("industrial_propellant", new Item(itemProps("industrial_propellant")));
    /** First-stage single-item mechanical-press output. A die assigns its actual caliber later. */
    public static Item CARTRIDGE_CASE_BLANK = register("cartridge_case_blank", new IndustryTaggedItem(itemProps("cartridge_case_blank").stacksTo(16)));
    /**
     * Calibre-bearing case; each configured stack receives the exact final
     * ammo-family max_stack_size component, while 99 is the safe 26.2 fallback
     * for old/unconfigured stacks.
     */
    public static Item CARTRIDGE_CASE = register("cartridge_case", new IndustryTaggedItem(
            itemProps("cartridge_case").stacksTo(Item.ABSOLUTE_MAX_STACK_SIZE)
    ));
    public static Item PRIMER = register("primer", new Item(itemProps("primer")));
    /** First-stage single-item mechanical-press output. A projectile die assigns its behavior later. */
    public static Item PROJECTILE_BLANK = register("projectile_blank", new IndustryTaggedItem(itemProps("projectile_blank").stacksTo(16)));
    /**
     * Projectile type and calibre are explicit custom data, not inferred from
     * material count. Configured stacks use the corresponding final ammo cap.
     */
    public static Item PROJECTILE_CORE = register("projectile_core", new IndustryTaggedItem(
            itemProps("projectile_core").stacksTo(Item.ABSOLUTE_MAX_STACK_SIZE)
    ));
    /** Reusable die held by a Create Deployer; keep_held_item makes the calibration physical. */
    public static Item PRESS_DIE = register("press_die", new IndustryTaggedItem(itemProps("press_die").stacksTo(1)));

    /** Neutral blank with a structural class; a reusable platform die forms its final identity. */
    public static Item GUN_COMPONENT_BLANK = register("gun_component_blank", new IndustryTaggedItem(itemProps("gun_component_blank").stacksTo(16)));
    /** Platform/kind are stored in custom data so one registry item serves all normal production assemblies. */
    public static Item GUN_COMPONENT = register("gun_component", new IndustryTaggedItem(itemProps("gun_component").stacksTo(16)));
    /** Durable, dismantled service component; deliberately cannot enter normal production terminal recipes. */
    public static Item SERVICE_COMPONENT = register("service_component", new IndustryTaggedItem(itemProps("service_component").stacksTo(1)));
    /** Neutral maintenance workpiece; retained only for old-world item compatibility after bench-native repair replaced this route. */
    public static Item SERVICE_PART_BLANK = register("service_part_blank", new IndustryTaggedItem(itemProps("service_part_blank").stacksTo(16)));
    /** Named legacy replacement part retained only for old-world compatibility after bench-native repair replaced this route. */
    public static Item SERVICE_PART = register("service_part", new IndustryTaggedItem(itemProps("service_part").stacksTo(16)));
    /** Blank sheets, master dossiers, and reusable production templates share one NBT-backed tooling medium. */
    public static Item GUN_BLUEPRINT = register("gun_blueprint", new IndustryTaggedItem(itemProps("gun_blueprint").stacksTo(1)));
    /** Native-durability tool consumed only after a successful service-bench transaction. */
    public static Item ARMORER_WRENCH = register("armorer_wrench", new Item(itemProps("armorer_wrench").stacksTo(1)));
    /** Basin-made named consumable used only by the service bench's server-side fouling-cleaning transaction. */
    public static Item MAINTENANCE_CLEANING_KIT = register("maintenance_cleaning_kit", new Item(
            itemProps("maintenance_cleaning_kit").stacksTo(16)
    ));

    public static AttachmentItem ATTACHMENT = register("attachment", new AttachmentItem(itemProps("attachment")));

    public static GunSmithTableItem GUN_SMITH_TABLE = register("gun_smith_table", new DefaultTableItem(ModBlocks.GUN_SMITH_TABLE, blockItemProps("gun_smith_table")));
    public static GunSmithTableItem WORKBENCH_111 = register("workbench_a", new GunSmithTableItem(ModBlocks.WORKBENCH_111, blockItemProps("workbench_a")));
    public static GunSmithTableItem WORKBENCH_211 = register("workbench_b", new GunSmithTableItem(ModBlocks.WORKBENCH_211, blockItemProps("workbench_b")));
    public static GunSmithTableItem WORKBENCH_121 = register("workbench_c", new GunSmithTableItem(ModBlocks.WORKBENCH_121, blockItemProps("workbench_c")));
    /** Dedicated GUI block for final cartridge assembly; it is intentionally not a Create Depot recipe. */
    public static Item CARTRIDGE_ASSEMBLY_MACHINE = register("cartridge_assembly_machine",
            new BlockItem(ModBlocks.CARTRIDGE_ASSEMBLY_MACHINE, blockItemProps("cartridge_assembly_machine")));
    /** Safe one-input recovery machine for stripped industrial equipment. */
    public static Item INDUSTRIAL_SALVAGE_STATION = register("industrial_salvage_station",
            new BlockItem(ModBlocks.INDUSTRIAL_SALVAGE_STATION, blockItemProps("industrial_salvage_station")));
    /** Ten-slot industrial dismantling/reassembly station, not a salvage replacement. */
    public static Item INDUSTRIAL_SERVICE_BENCH = register("industrial_service_bench",
            new BlockItem(ModBlocks.INDUSTRIAL_SERVICE_BENCH, blockItemProps("industrial_service_bench")));
    /** Stores up to four complete physical magazine stacks, including their remaining rounds. */
    public static Item MAGAZINE_POUCH = register("magazine_pouch", new MagazinePouchItem(itemProps("magazine_pouch")));
    /** Reusable inventory tool that accelerates, but never batch-skips, physical round loading. */
    public static Item MAGAZINE_LOADER = register("magazine_loader", new MagazineLoaderItem(itemProps("magazine_loader")));


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