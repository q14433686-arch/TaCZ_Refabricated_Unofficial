package com.tacz.guns.compat.jei.category;

import com.tacz.guns.industry.recipe.IndustryProcessDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;

/**
 * JEI counterpart to the synchronized Create-process projection.
 *
 * <p>Create Fly's native 26.2 recipe-viewer support is optional and its REI
 * integration is absent. Keeping this category on TACZ's already synchronized
 * {@link IndustryProcessDefinition} graph means JEI and REI expose the exact
 * same NBT-aware component/die routes instead of one viewer silently losing the
 * tree.</p>
 */
public final class IndustryProcessCategory implements IRecipeCategory<IndustryProcessDefinition> {
    private static final int INPUT_COLUMNS = 5;
    private static final int SLOT_SPACING = 19;

    private final IndustryProcessMachine machine;
    private final IRecipeType<IndustryProcessDefinition> type;
    private final IDrawable slot;
    private final IDrawable icon;

    public IndustryProcessCategory(IGuiHelper guiHelper, IndustryProcessMachine machine,
                                   IRecipeType<IndustryProcessDefinition> type) {
        this.machine = machine;
        this.type = type;
        this.slot = guiHelper.getSlotDrawable();
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, machine.workstationStack());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IndustryProcessDefinition recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.getInputs().size(); index++) {
            int x = 8 + (index % INPUT_COLUMNS) * SLOT_SPACING;
            int y = 8 + (index / INPUT_COLUMNS) * SLOT_SPACING;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .add(recipe.getInputs().get(index).createStack())
                    .setBackground(slot, -1, -1);
        }
        for (int index = 0; index < recipe.getOutputs().size(); index++) {
            int x = 140 + (index % 2) * SLOT_SPACING;
            int y = 18 + (index / 2) * SLOT_SPACING;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .add(recipe.getOutputs().get(index).createStack())
                    .setBackground(slot, -1, -1);
        }
    }

    @Override
    public IRecipeType<IndustryProcessDefinition> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(machine.translationKey());
    }

    @Override
    public int getWidth() {
        return 184;
    }

    @Override
    public int getHeight() {
        return 72;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }
}
