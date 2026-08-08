package com.tacz.guns.compat.jei.category;

import com.tacz.guns.GunMod;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.magazine.GunFeedReferenceEntry;
import com.tacz.guns.init.ModItems;
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

/**
 * A JEI reference page for the authoritative gun → ammunition → carrier
 * relationship. It intentionally has no transfer/crafting semantics.
 */
public final class GunFeedReferenceCategory implements IRecipeCategory<GunFeedReferenceEntry> {
    public static final IRecipeType<GunFeedReferenceEntry> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_feed_reference"),
            GunFeedReferenceEntry.class
    );

    private static final int WIDTH = 206;
    private static final int HEIGHT = 83;
    private final IDrawable slotDraw;
    private final IDrawable iconDraw;

    public GunFeedReferenceCategory(IGuiHelper guiHelper) {
        this.slotDraw = guiHelper.getSlotDrawable();
        this.iconDraw = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ModItems.MAGAZINE.getDefaultInstance());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GunFeedReferenceEntry entry, IFocusGroup focuses) {
        // Inputs deliberately model "things this gun accepts", while the gun
        // is the output. That makes U on an ammo/magazine list the guns that
        // use it, and R on a gun open its exact feed reference.
        builder.addSlot(RecipeIngredientRole.INPUT, 8, 23)
                .addItemStacks(entry.getAmmoStacks())
                .setBackground(slotDraw, -1, -1)
                .setSlotName("accepted_ammunition");
        if (entry.hasDeclaredPhysicalCarrier()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 80, 23)
                    .addItemStacks(entry.getCarrierStacks())
                    .setBackground(slotDraw, -1, -1)
                    .setSlotName("carrier_or_loader");
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 176, 23)
                .add(entry.getGunStack())
                .setBackground(slotDraw, -1, -1)
                .setSlotName("gun");
    }

    @Override
    public void draw(GunFeedReferenceEntry entry, IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.reference_only"), 4, 2, 0xFFAA5555, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.ammo"), 8, 14, 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.carrier"), 76, 14, 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.gun"), 176, 14, 0xFFAAAAAA, false);
        graphics.text(font, "→", 145, 27, 0xFFFFFFFF, false);

        GunFeedDefinition definition = entry.getFeedDefinition();
        if (definition == null) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.no_carrier"), 4, 51, 0xFFAAAAAA, false);
            return;
        }

        Component mechanism = Component.translatable(
                "jei.tacz.gun_feed.mechanism." + definition.getMechanism().serializedName());
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.mechanism", mechanism,
                definition.getMagazineCapacity()), 4, 51, 0xFFAAAAAA, false);
        if (entry.hasFixedInternalFeed()) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.fixed_feed"), 4, 63, 0xFFAAAAAA, false);
            return;
        }
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.family", definition.getMagazineFamily()),
                4, 63, 0xFFAAAAAA, false);
        if (definition.getFeedStandardId() != null) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.standard", definition.getFeedStandardId()),
                    4, 74, 0xFF777777, false);
        }
    }

    @Override
    public Identifier getIdentifier(GunFeedReferenceEntry entry) {
        return entry.getGunId();
    }

    @Override
    public IRecipeType<GunFeedReferenceEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.tacz.gun_feed.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return iconDraw;
    }
}
