package com.tacz.guns.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.compat.jei.category.AttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.GunSmithTableCategory;
import com.tacz.guns.compat.jei.entry.AttachmentQueryEntry;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JeiPlugin
public class GunModPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "jei");

    private Map<Identifier, IRecipeType<GunSmithTableRecipe>> recipeTypeMap = new HashMap<>();

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        recipeTypeMap.clear();
        var map = TimelessAPI.getAllCommonBlockIndex();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            IRecipeType<GunSmithTableRecipe> type = IRecipeType.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table/" + entry.getKey().toString().replace(':', '_')), GunSmithTableRecipe.class);
            registration.addRecipeCategories(new GunSmithTableCategory(registration.getJeiHelpers().getGuiHelper(), icon, type, item.getName(icon)));
            recipeTypeMap.put(entry.getKey(), type);
        }
        registration.addRecipeCategories(new AttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        // 第 12 轮：与 GunSmithTableScreen 一致，改用 CommonAssetsManager.get()。
        // getInstance() 是纯服务端实例，在多人客户端恒为 null —— JEI 在单人能用
        // 只是因为同 JVM 共享了服务端实例，连专用服务器时同样会空。
        List<GunSmithTableRecipe> recipes = new java.util.ArrayList<>();
        for (var e : com.tacz.guns.resource.CommonAssetsManager.get().getAllTableRecipes()) {
            if (e.getValue() != null && e.getValue().getResult() != null) {
                GunSmithTableRecipe r = new GunSmithTableRecipe(e.getKey(), e.getValue());
                try {
                    r.init();   // 解析 raw result，否则 getResult() 恒为 EMPTY
                } catch (RuntimeException ex) {
                    // 一条坏配方绝不能中断整个循环 —— 否则全部工作台配方都会从 JEI 消失。
                    GunMod.LOGGER.error("Failed to init gun smith table recipe {} for JEI, skipping it", e.getKey(), ex);
                    continue;
                }
                recipes.add(r);
            }
        }

        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                List<GunSmithTableRecipe> recipeList = blockIndex.getFilter().filter(recipes, GunSmithTableRecipe::getId);
                recipeList.removeIf(recipe -> {
                    return blockIndex.getData().getTabs().stream().noneMatch(tab -> Objects.equals(tab.id(), recipe.getResult().getGroup()));
                });
                registration.addRecipes(entry.getValue(), recipeList);
            });
        }

        registration.addRecipes(AttachmentQueryCategory.ATTACHMENT_QUERY, AttachmentQueryEntry.getAllAttachmentQueryEntries());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                ItemStack stack = BlockItemBuilder.create(blockIndex.getBlock()).setId(entry.getKey()).build();
                registration.addCraftingStation(entry.getValue(), stack);
            });

        }

    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.AMMO, GunModSubtype.getAmmoSubtype());
        registration.registerSubtypeInterpreter(ModItems.ATTACHMENT, GunModSubtype.getAttachmentSubtype());
        registration.registerSubtypeInterpreter(ModItems.AMMO_BOX, GunModSubtype.getAmmoBoxSubtype());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_111, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_121, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_211, GunModSubtype.getTableSubType());
        GunItemManager.getAllGunItems().forEach(item -> registration.registerSubtypeInterpreter(item, GunModSubtype.getGunSubtype()));
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }
}
