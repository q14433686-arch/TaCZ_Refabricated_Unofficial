package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.category.AttachmentQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunSmithTableCategory;
import cn.sh1rocu.tacz.compat.rei.display.AttachmentQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import cn.sh1rocu.tacz.compat.rei.entry.AttachmentQueryEntry;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
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
    public static final CategoryIdentifier<AttachmentQueryDisplay> ATTACHMENT_QUERY = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/attachment_query");

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
            if (e.getValue() == null || e.getValue().getResult() == null) {
                continue;
            }
            try {
                GunSmithTableRecipe r = new GunSmithTableRecipe(e.getKey(), e.getValue());
                r.init();
                if (r.getResult() == null || r.getResult().getResult().isEmpty()) {
                    com.tacz.guns.GunMod.LOGGER.warn("[REI] Gun smith table recipe {} has empty result after init(), skipping", e.getKey());
                    continue;
                }
                recipes.add(r);
            } catch (RuntimeException ex) {
                // 单条配方 init 失败只记录、不打断整个 REI 注册。
                // 与 JEI 侧对称，避免 CUSTOM result 格式异常一类的问题清空整个 REI 分类。
                com.tacz.guns.GunMod.LOGGER.warn("[REI] Failed to init gun smith table recipe {}, skipping", e.getKey(), ex);
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
    }
}
