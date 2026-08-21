package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.category.AmmoQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.AttachmentQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunSmithTableCategory;
import cn.sh1rocu.tacz.compat.rei.display.AmmoQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.AttachmentQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import cn.sh1rocu.tacz.compat.rei.entry.AttachmentQueryEntry;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.AmmoBoxItem;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.item.GunSmithTableItem;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class REIClientPlugin implements me.shedaniel.rei.api.client.plugins.REIClientPlugin {
    public static final CategoryIdentifier<AttachmentQueryDisplay> ATTACHMENT_QUERY =
            CategoryIdentifier.of(GunMod.MOD_ID, "plugins/attachment_query");
    public static final CategoryIdentifier<AmmoQueryDisplay> AMMO_QUERY =
            CategoryIdentifier.of(GunMod.MOD_ID, "plugins/ammo_query");

    public static final Map<Identifier, CategoryIdentifier<GunSmithTableDisplay>> displays = new HashMap<>();

    @Override
    public void registerCategories(CategoryRegistry registry) {
        var map = TimelessAPI.getAllCommonBlockIndex();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            // 根据需要的枪械工作台类型生成动态id
            CategoryIdentifier<GunSmithTableDisplay> id = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/gun_smith_table/" + entry.getKey().toString().replace(':', '_'));
            registry.add(new GunSmithTableCategory(Component.translatable(entry.getValue().getPojo().getName()), icon, id));
            displays.put(entry.getKey(), id);
            registry.addWorkstations(id, EntryStacks.of(icon));
        }
        registry.add(new AttachmentQueryCategory());
        registry.add(new AmmoQueryCategory());
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        // TaCZ/LR 的物品是「一个注册物品 + NBT 指定具体内容」：所有枪共用
        // tacz:modern_kinetic_gun，所有弹药共用 tacz:ammo，配件共用 tacz:attachment，
        // 工作台共用 tacz:workbench_a/b/c。具体是哪一把/哪种，全看 GunId/AmmoId/
        // AttachmentId/BlockId NBT。
        //
        // 创造模式标签页通过 fillItemCategory() 用 builder 生成带正确 NBT 的物品堆，
        // JEI 的物品列表遍历创造标签页，所以天然正确；但 REI 的物品面板默认列出
        // 注册表里的【每一个物品】，包括没有任何 NBT 的裸 tacz:modern_kinetic_gun。
        // 玩家从 REI 取到裸枪时：没有 GunId -> 找不到 display -> 紫黑贴图，名字回退到
        // 不存在的 item.tacz.modern_kinetic_gun 翻译键 -> 显示 "name." / 原始键。
        // 这正是用户报告的「所有枪紫黑、三个工作台紫黑、LR 只拿到测试手雷」的根因。
        //
        // 修复：把与创造标签页同源的带 NBT 变体显式注册进 REI 面板，并移除裸物品，
        // 使 REI 与 JEI/创造栏表现一致。
        for (GunTabType type : GunTabType.values()) {
            AbstractGunItem.fillItemCategory(type).forEach(stack -> registry.addEntry(EntryStacks.of(stack)));
        }
        AmmoItem.fillItemCategory().forEach(stack -> registry.addEntry(EntryStacks.of(stack)));
        for (AttachmentType type : AttachmentType.values()) {
            AttachmentItem.fillItemCategory(type).forEach(stack -> registry.addEntry(EntryStacks.of(stack)));
        }
        GunSmithTableItem.fillItemCategory().forEach(stack -> registry.addEntry(EntryStacks.of(stack)));

        // 弹药箱也有等级/创造模式的 NBT 变体（与创造标签页 AmmoBoxItem.fillItemCategory 同源）。
        ItemStack ammoBox = ModItems.AMMO_BOX.getDefaultInstance();
        if (ammoBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iAmmoBox) {
            registry.addEntry(EntryStacks.of(iAmmoBox.setAmmoLevel(ammoBox.copy(), AmmoBoxItem.IRON_LEVEL)));
            registry.addEntry(EntryStacks.of(iAmmoBox.setAmmoLevel(ammoBox.copy(), AmmoBoxItem.GOLD_LEVEL)));
            registry.addEntry(EntryStacks.of(iAmmoBox.setAmmoLevel(ammoBox.copy(), AmmoBoxItem.DIAMOND_LEVEL)));
            registry.addEntry(EntryStacks.of(iAmmoBox.setCreative(ammoBox.copy(), false)));
            registry.addEntry(EntryStacks.of(iAmmoBox.setCreative(ammoBox.copy(), true)));
        }
        registry.removeEntry(EntryStacks.of(ModItems.AMMO_BOX));

        // LRTactical 的投掷物/近战/消耗品同理：lrtactical:throwable/melee/consumable
        // 三个注册物品靠 NBT 区分具体内容，裸物品无功能。注册带 NBT 的变体。
        me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes()
                .forEach(index -> registry.addEntry(EntryStacks.of(index.createItemStack())));
        me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeIndexes()
                .forEach(index -> registry.addEntry(EntryStacks.of(index.createItemStack())));
        me.xjqsh.lrtactical.api.LrTacticalAPI.getConsumableIndexes()
                .forEach(index -> registry.addEntry(EntryStacks.of(index.createItemStack())));

        // 移除没有 NBT 的裸注册物品，避免玩家再取到紫黑的占位条目。
        registry.removeEntry(EntryStacks.of(ModItems.MODERN_KINETIC_GUN));
        registry.removeEntry(EntryStacks.of(ModItems.AMMO));
        registry.removeEntry(EntryStacks.of(ModItems.ATTACHMENT));
        registry.removeEntry(EntryStacks.of(ModItems.WORKBENCH_111));
        registry.removeEntry(EntryStacks.of(ModItems.WORKBENCH_211));
        registry.removeEntry(EntryStacks.of(ModItems.WORKBENCH_121));
        registry.removeEntry(EntryStacks.of(me.xjqsh.lrtactical.init.ModItems.THROWABLE));
        registry.removeEntry(EntryStacks.of(me.xjqsh.lrtactical.init.ModItems.MELEE));
        registry.removeEntry(EntryStacks.of(me.xjqsh.lrtactical.init.ModItems.CONSUMABLE));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        if (Minecraft.getInstance().level == null) return;
        // 第 20 轮修复：本类此前一直沿用 CommonAssetsManager.getInstance() + RecipeManager，
        // 那是**纯服务端**路径（recipeManager 只在 AddReloadListenerEvent 里由
        // event.getServerResources() 赋值），在多人客户端上恒为 null ——
        // 于是 REI 用户连专用服务器时看不到任何 TACZ 配方，且因上面有 return 保护而**静默为空**。
        //
        // 第 12/13 轮已把 GunSmithTableScreen 与 JEI 的 GunModPlugin 迁到同步来的
        // DataType.RECIPES 通道，但**漏了 REI 这一处**（fabric.mod.json 里 rei_client
        // 与 rei_common 两个 entrypoint 都是活的，所以这条路径确实会被执行）。
        // 此处与 GunModPlugin#registerRecipes 保持逐行一致。
        //
        // 注意 init() 不可省：GunSmithTableResult 是两阶段初始化，Gson 只填 RawGunTableResult，
        // 不调 init() 则 getResult() 恒为 ItemStack.EMPTY、getGroup() 为 null，
        // 会在下面的 tabs 匹配处被全部过滤掉（第 13 轮踩过的坑）。init() 幂等。
        List<GunSmithTableRecipe> recipes = new java.util.ArrayList<>();
        for (var e : com.tacz.guns.resource.CommonAssetsManager.get().getAllTableRecipes()) {
            if (e.getValue() != null && e.getValue().getResult() != null) {
                GunSmithTableRecipe r = new GunSmithTableRecipe(e.getKey(), e.getValue());
                try {
                    r.init();
                } catch (RuntimeException ex) {
                    // 一条坏配方绝不能中断整个循环 —— 否则全部工作台配方都会从 REI 消失。
                    GunMod.LOGGER.error("Failed to init gun smith table recipe {} for REI, skipping it", e.getKey(), ex);
                    continue;
                }
                recipes.add(r);
            }
        }

        for (var entry : displays.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                List<GunSmithTableRecipe> recipeList = blockIndex.getFilter().filter(recipes, GunSmithTableRecipe::getId);
                recipeList.removeIf(recipe ->
                        blockIndex.getData().getTabs().stream().noneMatch(tab -> Objects.equals(tab.id(), recipe.getResult().getGroup())));
                recipeList.forEach(recipe -> registry.add(new GunSmithTableDisplay(recipe, entry)));
            });
        }

        AttachmentQueryEntry.getAllAttachmentQueryEntries().forEach(entry ->
                registry.add(new AttachmentQueryDisplay(entry)));
        AmmoQueryEntry.getAllAmmoQueryEntries().forEach(entry ->
                registry.add(new AmmoQueryDisplay(entry)));
    }
}
