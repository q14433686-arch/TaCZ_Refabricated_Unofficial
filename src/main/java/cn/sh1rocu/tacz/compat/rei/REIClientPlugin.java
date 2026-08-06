package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.category.AttachmentQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.CartridgeAssemblyCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunSmithTableCategory;
import cn.sh1rocu.tacz.compat.rei.category.IndustryProcessCategory;
import cn.sh1rocu.tacz.compat.rei.display.AttachmentQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.CartridgeAssemblyDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import cn.sh1rocu.tacz.compat.rei.display.IndustryProcessDisplay;
import cn.sh1rocu.tacz.compat.rei.display.IndustryProcessDisplayGenerator;
import cn.sh1rocu.tacz.compat.rei.entry.AttachmentQueryEntry;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
import com.tacz.guns.init.ModItems;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class REIClientPlugin implements me.shedaniel.rei.api.client.plugins.REIClientPlugin {
    public static final CategoryIdentifier<AttachmentQueryDisplay> ATTACHMENT_QUERY = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/attachment_query");
    public static final CategoryIdentifier<CartridgeAssemblyDisplay> CARTRIDGE_ASSEMBLY =
            CategoryIdentifier.of(GunMod.MOD_ID, "industry/cartridge_assembly_machine");

    public static final Map<Identifier, CategoryIdentifier<GunSmithTableDisplay>> displays = new HashMap<>();
    private static final Map<IndustryProcessMachine, CategoryIdentifier<IndustryProcessDisplay>> INDUSTRY_CATEGORIES =
            new EnumMap<>(IndustryProcessMachine.class);

    static {
        for (IndustryProcessMachine machine : IndustryProcessMachine.values()) {
            INDUSTRY_CATEGORIES.put(machine, CategoryIdentifier.of(GunMod.MOD_ID, "industry/" + machine.id()));
        }
    }

    public static CategoryIdentifier<IndustryProcessDisplay> getIndustryCategory(IndustryProcessMachine machine) {
        return INDUSTRY_CATEGORIES.get(machine);
    }

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
        registry.add(new CartridgeAssemblyCategory());
        registry.addWorkstations(CARTRIDGE_ASSEMBLY, EntryStacks.of(ModItems.CARTRIDGE_ASSEMBLY_MACHINE.getDefaultInstance()));
        for (IndustryProcessMachine machine : IndustryProcessMachine.values()) {
            CategoryIdentifier<IndustryProcessDisplay> id = getIndustryCategory(machine);
            registry.add(new IndustryProcessCategory(machine, id));
            registry.addWorkstations(id, EntryStacks.of(machine.workstationStack()));
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // REI initializes plugins before a remote server has synchronized the
        // TACZ common-data cache. These live generators deliberately register
        // before the level guard and resolve the cache only when a player asks
        // for a recipe/usage, so molds and components never become a one-time
        // empty snapshot at the title screen.
        for (IndustryProcessMachine machine : IndustryProcessMachine.values()) {
            CategoryIdentifier<IndustryProcessDisplay> id = getIndustryCategory(machine);
            registry.registerDisplayGenerator(id, new IndustryProcessDisplayGenerator(machine, id));
        }
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

        // IndustryProcessDisplayGenerator supplies Create process displays live
        // above. It intentionally replaces a one-time static cache snapshot.

        for (var entry : com.tacz.guns.resource.CommonAssetsManager.get().getAllCartridgeAssemblyRecipes()) {
            if (entry.getValue() != null) {
                registry.add(new CartridgeAssemblyDisplay(entry.getKey(), entry.getValue()));
            }
        }
    }
}
