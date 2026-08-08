package com.tacz.guns.compat.jei;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import java.util.Optional;

/**
 * Turns JEI's normal transfer button into a precise Gunsmith Table selection.
 *
 * <p>The table consumes directly from the player inventory and has no ghost
 * crafting grid, so moving stacks into slots would be both misleading and
 * unsafe. Pressing JEI's transfer button while the matching table is open
 * instead locks the exact synchronized recipe in the table UI. Crafting still
 * travels through {@code ClientMessageCraft} and the server's existing
 * block/filter/material validation; this handler never sends a craft packet or
 * changes an inventory.</p>
 */
public final class GunSmithTableRecipeTransferHandler
        implements IRecipeTransferHandler<GunSmithTableMenu, GunSmithTableRecipe> {
    private static final IRecipeTransferError LOCK_PREVIEW = new IRecipeTransferError() {
        @Override
        public IRecipeTransferError.Type getType() {
            return IRecipeTransferError.Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            // Keep JEI's familiar normal + button appearance; this is a
            // navigation action, not a missing-material warning.
            return 0;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("jei.tacz.gun_smith_table.transfer.lock"));
        }
    };

    private final Identifier blockId;
    private final IRecipeType<GunSmithTableRecipe> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    public GunSmithTableRecipeTransferHandler(Identifier blockId, IRecipeType<GunSmithTableRecipe> recipeType,
                                              IRecipeTransferHandlerHelper helper) {
        this.blockId = blockId;
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<? extends GunSmithTableMenu> getContainerClass() {
        return GunSmithTableMenu.class;
    }

    @Override
    public Optional<MenuType<GunSmithTableMenu>> getMenuType() {
        // ExtendedMenuType carries the current table Identifier as extra open
        // data. The handler additionally checks that exact Identifier below,
        // so narrowing by the common menu type would add no safety.
        return Optional.empty();
    }

    @Override
    public IRecipeType<GunSmithTableRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public IRecipeTransferError transferRecipe(GunSmithTableMenu container, GunSmithTableRecipe recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (recipe == null || !blockId.equals(container.getBlockId())) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("jei.tacz.gun_smith_table.transfer.unavailable"));
        }
        if (!doTransfer) {
            // JEI calls once to decide whether the button is usable. Selection
            // must happen only after the player actually presses it.
            return LOCK_PREVIEW;
        }
        GunSmithTableScreen screen = GunSmithTableScreen.getRecipeViewerParent(container);
        if (screen == null || !screen.selectRecipeFromViewer(recipe.getId())) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("jei.tacz.gun_smith_table.transfer.unavailable"));
        }
        return null;
    }
}
