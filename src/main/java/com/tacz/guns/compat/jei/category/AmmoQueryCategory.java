package com.tacz.guns.compat.jei.category;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.init.ModCreativeTabs;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Displays every loaded gun that consumes a selected TACZ ammunition type. */
public final class AmmoQueryCategory implements IRecipeCategory<AmmoQueryEntry> {
    public static final IRecipeType<AmmoQueryEntry> AMMO_QUERY = IRecipeType.create(
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo_query"), AmmoQueryEntry.class);

    private static final Component TITLE = Component.translatable("jei.tacz.ammo_query.title");

    private final IDrawable slotDraw;
    private final IDrawable iconDraw;

    public AmmoQueryCategory(IGuiHelper guiHelper) {
        this.slotDraw = guiHelper.getSlotDrawable();
        this.iconDraw = guiHelper.createDrawableIngredient(
                VanillaTypes.ITEM_STACK, ModCreativeTabs.AMMO_TAB.getIconItem());
    }

    @Override
    public void draw(AmmoQueryEntry entry, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        if (!entry.getExtraGunStacks().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            guiGraphics.text(font, Component.translatable("jei.tacz.ammo_query.more"),
                    128, 134, 0xFF555555, false);
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AmmoQueryEntry entry, IFocusGroup focuses) {
        List<ItemStack> gunStacks = entry.getGunStacks();
        List<ItemStack> extraGunStacks = entry.getExtraGunStacks();

        // Keep the queried ammunition in the same prominent top-centre position as the existing
        // attachment query; the compatible guns fill rows below it.
        builder.addSlot(RecipeIngredientRole.OUTPUT, 72, 0)
                .add(entry.getAmmoStack())
                .setBackground(slotDraw, -1, -1);

        int xOffset = 0;
        int yOffset = 20;
        for (int i = 0; i < gunStacks.size(); i++) {
            int column = i % 9;
            int row = i / 9;
            xOffset = column * 18;
            yOffset = 20 + row * 18;
            builder.addSlot(RecipeIngredientRole.INPUT, xOffset, yOffset)
                    .add(gunStacks.get(i))
                    .setBackground(slotDraw, -1, -1);
        }

        if (!extraGunStacks.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, xOffset + 18, yOffset)
                    .addItemStacks(extraGunStacks)
                    .setBackground(slotDraw, -1, -1);
        }
    }

    @Override
    public IRecipeType<AmmoQueryEntry> getRecipeType() {
        return AMMO_QUERY;
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 145;
    }

    @Override
    public IDrawable getIcon() {
        return iconDraw;
    }
}
