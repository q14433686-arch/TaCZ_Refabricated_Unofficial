package com.tacz.guns.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.compat.jei.category.AmmoQueryCategory;
import com.tacz.guns.compat.jei.category.GunAmmoQueryCategory;
import com.tacz.guns.compat.jei.category.GunAttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.AttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.GunSmithTableCategory;
import com.tacz.guns.compat.jei.entry.AttachmentQueryEntry;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.ClientWorkbenchRecipes;
import com.tacz.guns.compat.recipeviewer.AttachmentQueryData;
import com.tacz.guns.compat.recipeviewer.GunAmmoQueryEntry;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

@JeiPlugin
public class GunModPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "jei");

    private Map<Identifier, IRecipeType<GunSmithTableRecipe>> recipeTypeMap = new HashMap<>();

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        recipeTypeMap.clear();
        var map = CommonNetworkCache.INSTANCE.getAllBlocks();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            IRecipeType<GunSmithTableRecipe> type = IRecipeType.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table/" + entry.getKey().toString().replace(':', '_')), GunSmithTableRecipe.class);
            registration.addRecipeCategories(new GunSmithTableCategory(registration.getJeiHelpers().getGuiHelper(), icon, type, Component.translatable(entry.getValue().getPojo().getName())));
            recipeTypeMap.put(entry.getKey(), type);
        }
        registration.addRecipeCategories(
                new AttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new GunAmmoQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new GunAttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new AmmoQueryCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        ClientWorkbenchRecipes.byTable().forEach((table, recipes) -> {
            var category = recipeTypeMap.get(table);
            if (category != null) registration.addRecipes(category, recipes);
        });

        var attachments = AttachmentQueryData.load();
        registration.addRecipes(AttachmentQueryCategory.ATTACHMENT_QUERY,
                attachments.attachmentQueries().stream().map(AttachmentQueryEntry::new).toList());
        registration.addRecipes(GunAttachmentQueryCategory.GUN_ATTACHMENT_QUERY, attachments.gunAttachmentQueries());
        var ammo = GunAmmoQueryEntry.getAllGunAmmoQueryEntries();
        registration.addRecipes(GunAmmoQueryCategory.GUN_AMMO_QUERY, ammo);
        registration.addRecipes(AmmoQueryCategory.AMMO_QUERY, AmmoQueryEntry.getAllAmmoQueryEntries(ammo));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var entry : recipeTypeMap.entrySet()) {
            var block = CommonNetworkCache.INSTANCE.getBlockIndex(entry.getKey());
            if (block != null) {
                ItemStack stack = BlockItemBuilder.create(block.getBlock()).setId(entry.getKey()).build();
                registration.addCraftingStation(entry.getValue(), stack);
            }
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (var type : recipeTypeMap.values()) {
            registration.addRecipeTransferHandler(new GunSmithTableTransferHandler(type, registration.getTransferHelper()), type);
        }
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.AMMO, GunModSubtype.getAmmoSubtype());
        registration.registerSubtypeInterpreter(ModItems.ATTACHMENT, GunModSubtype.getAttachmentSubtype());
        registration.registerSubtypeInterpreter(ModItems.AMMO_BOX, GunModSubtype.getAmmoBoxSubtype());
        registration.registerSubtypeInterpreter(ModItems.GUN_SMITH_TABLE, GunModSubtype.getTableSubType());
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
