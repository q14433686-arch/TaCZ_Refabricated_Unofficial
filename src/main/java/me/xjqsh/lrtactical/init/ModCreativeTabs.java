package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

/**
 * 创造模式标签页。
 *
 * <h2>为什么需要它</h2>
 * 26.2 的物品<b>不会自动出现在任何标签页里</b>。若不显式加入某个 tab，
 * 玩家在创造栏中<b>根本找不到</b>该物品（只能用 {@code /give} 拿到）。
 * 这正是第 4 步「物品注册了却找不到」的原因之一。
 *
 * <h2>填充策略</h2>
 * 遍历数据包中定义的每一种投掷物（{@code index/throwable/*.json}）并逐一列出。
 *
 * <p>若没有安装任何内容包，索引为空，标签页也就是空的 ——
 * 这是<b>正确行为</b>：本移植不打包原作美术资源，实际内容由第三方内容包提供。
 *
 * <p>（第 5 步之前这里曾放一个「裸物品」以便验证注册链路；
 * 资源层接上后已移除 —— 那个物品拿到手也投不出去，留着反而误导。）
 */
public final class ModCreativeTabs {
    private static final net.minecraft.resources.Identifier[] BUILTIN_TEST_THROWABLES = new net.minecraft.resources.Identifier[] {
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_grenade"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_sticky_grenade"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_smoke_grenade"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_gas_grenade"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_splash_grenade"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_flashbang"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_c4")
    };

    private static final net.minecraft.resources.Identifier[] BUILTIN_TEST_MELEE = new net.minecraft.resources.Identifier[] {
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_knife")
    };

    private static final net.minecraft.resources.Identifier[] BUILTIN_TEST_CONSUMABLES = new net.minecraft.resources.Identifier[] {
            net.minecraft.resources.Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "test_medkit")
    };

    public static final CreativeModeTab THROWABLE_TAB = register("throwable",
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + EquipmentMod.MOD_ID + ".throwable"))
                    .icon(() -> ModItems.THROWABLE.getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 先放内置测试物品的兜底栈。
                        //
                        // LRTactical 的真实内容来自 data/<ns>/index/**，而这批索引走服务端数据包通道；
                        // 创造标签页内容却可能在客户端主菜单资源重载阶段就被缓存。这样即使本 jar 内置了
                        // data/lrtactical/index/** 测试数据，第一次打开创造栏也可能仍是空页。
                        // 这里直接按已知测试 id 生成可见栈，保证「新增测试物品」始终能被找到；等索引加载/同步后，
                        // 下方的数据驱动列表会补充第三方内容包条目。
                        addBuiltinTestStacks(output);

                        me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes()
                                .forEach(index -> output.accept(index.createItemStack()));
                        // 近战武器与投掷物共用一个标签页 —— 内容包通常两者都有，
                        // 分成两个页反而要来回切。
                        me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeIndexes()
                                .forEach(index -> output.accept(index.createItemStack()));
                        me.xjqsh.lrtactical.api.LrTacticalAPI.getConsumableIndexes()
                                .forEach(index -> output.accept(index.createItemStack()));
                        output.accept(ModItems.DETONATOR);
                    })
                    .build());

    private static void addBuiltinTestStacks(CreativeModeTab.Output output) {
        for (net.minecraft.resources.Identifier id : BUILTIN_TEST_THROWABLES) {
            if (me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getThrowableIndex(id) != null) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ModItems.THROWABLE);
            ((me.xjqsh.lrtactical.api.item.IThrowable) ModItems.THROWABLE).setId(stack, id);
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
            output.accept(stack);
        }
        for (net.minecraft.resources.Identifier id : BUILTIN_TEST_MELEE) {
            if (me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getMeleeIndex(id) != null) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ModItems.MELEE);
            ((me.xjqsh.lrtactical.api.item.IMeleeWeapon) ModItems.MELEE).setId(stack, id);
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
            output.accept(stack);
        }
        for (net.minecraft.resources.Identifier id : BUILTIN_TEST_CONSUMABLES) {
            if (me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getConsumableIndex(id) != null) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ModItems.CONSUMABLE);
            ((me.xjqsh.lrtactical.api.item.IConsumable) ModItems.CONSUMABLE).setId(stack, id);
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
            output.accept(stack);
        }
    }

    private ModCreativeTabs() {
    }

    public static void init() {
        // 触发静态初始化，完成注册
    }

    private static CreativeModeTab register(String name, CreativeModeTab tab) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, name), tab);
    }
}
