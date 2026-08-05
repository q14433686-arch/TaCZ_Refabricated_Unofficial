package com.tacz.guns.compat.jei.category;

import com.tacz.guns.GunMod;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.init.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** JEI mirror of the dedicated explicit-slot cartridge assembler. */
public final class CartridgeAssemblyCategory implements IRecipeCategory<CartridgeAssemblyDefinition> {
    public static final IRecipeType<CartridgeAssemblyDefinition> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "cartridge_assembly_machine"), CartridgeAssemblyDefinition.class
    );

    private final IDrawable slot;
    private final IDrawable icon;

    public CartridgeAssemblyCategory(IGuiHelper guiHelper) {
        this.slot = guiHelper.getSlotDrawable();
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ModItems.CARTRIDGE_ASSEMBLY_MACHINE.getDefaultInstance());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CartridgeAssemblyDefinition recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 16, 8).add(recipe.createCasePreview()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 52, 8).add(recipe.createProjectilePreview()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 16, 34).add(recipe.createPrimerPreview()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 52, 34).add(recipe.createPropellantPreview()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 130, 21).add(recipe.createResult()).setBackground(slot, -1, -1);
    }

    @Override
    public IRecipeType<CartridgeAssemblyDefinition> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.tacz.cartridge_assembly_machine");
    }

    @Override
    public int getWidth() {
        return 170;
    }

    @Override
    public int getHeight() {
        return 60;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }
}
