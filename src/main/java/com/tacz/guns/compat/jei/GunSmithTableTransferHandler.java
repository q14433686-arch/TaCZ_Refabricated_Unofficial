package com.tacz.guns.compat.jei;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * JEI「把配方一键填入枪械工作台」的转移处理器。
 *
 * <p>由 {@code GunModPlugin#registerRecipeTransferHandlers} 为每种配方类型各建一个实例。
 * 关键在于把"能不能填"与"填不填"分开：JEI 在只有 mob 悬停时会先调一次
 * {@code doTransfer=false} 做可行性判定，那一次<b>不能</b>真的改动当前选中项，
 * 否则悬停一下就把玩家正在看的分页切走了。</p>
 */
record GunSmithTableTransferHandler(IRecipeType<GunSmithTableRecipe> type, IRecipeTransferHandlerHelper helper)
        implements IRecipeTransferHandler<GunSmithTableMenu, GunSmithTableRecipe> {
    @Override public Class<GunSmithTableMenu> getContainerClass() { return GunSmithTableMenu.class; }
    @Override public Optional<MenuType<GunSmithTableMenu>> getMenuType() { return Optional.of(GunSmithTableMenu.TYPE); }
    @Override public IRecipeType<GunSmithTableRecipe> getRecipeType() { return type; }

    private @Nullable IRecipeTransferError select(GunSmithTableMenu menu, GunSmithTableRecipe recipe, boolean commit) {
        if (menu.selectFromViewer(recipe.getId(), commit)) return null;
        return helper.createUserErrorWithTooltip(Component.translatable("jei.tacz.gun_smith_table.recipe_unavailable"));
    }

    // JEI 30.13 uses this overload; checking feasibility must not change selection.
    @Override public @Nullable IRecipeTransferError transferRecipe(GunSmithTableMenu menu, GunSmithTableRecipe recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        return select(menu, recipe, doTransfer);
    }
}
