package com.tacz.guns.test;

import com.tacz.guns.compat.jei.category.AmmoQueryCategory;
import com.tacz.guns.compat.jei.category.GunAmmoQueryCategory;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Test-mod lifecycle listener, not a second recipe registrar. No JEI classes in the baseline runtime. */
public final class JeiViewerSmoke implements ViewerSmoke, IModPlugin {
    private static IJeiRuntime runtime;
    @Override public Identifier getPluginUid() { return Identifier.parse("tacz_port_test:runtime_probe"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    @Override public boolean forward(Identifier id) {
        return runtime != null && runtime.getRecipeManager().createRecipeLookup(GunAmmoQueryCategory.GUN_AMMO_QUERY).get()
                .anyMatch(entry -> ViewerSmoke.isGun(entry.getGunStack(), id));
    }
    @Override public boolean reverse(Identifier id) {
        return runtime != null && runtime.getRecipeManager().createRecipeLookup(AmmoQueryCategory.AMMO_QUERY).get()
                .flatMap(entry -> entry.getGunStacks().stream()).anyMatch(stack -> ViewerSmoke.isGun(stack, id));
    }
    @Override public Set<Identifier> workbenchRecipes(Identifier table, @Nullable ItemStack outputFocus) {
        if (runtime == null) return Set.of();
        var recipes = runtime.getRecipeManager();
        var id = Identifier.parse("tacz:gun_smith_table/" + table.toString().replace(':', '_'));
        var type = recipes.getRecipeType(id, GunSmithTableRecipe.class).orElse(null);
        if (type == null) return Set.of();
        var query = recipes.createRecipeLookup(type);
        if (outputFocus != null) query.limitFocus(List.of(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, outputFocus)));
        return query.get().map(GunSmithTableRecipe::getId).collect(Collectors.toSet());
    }

    @Override public void open(Identifier gun) {
        var manager = runtime.getRecipeManager();
        var type = GunAmmoQueryCategory.GUN_AMMO_QUERY;
        var match = manager.createRecipeLookup(type).get().filter(entry -> ViewerSmoke.isGun(entry.getGunStack(), gun)).toList();
        if (match.size() != 1) throw new AssertionError("Expected one JEI recipe for " + gun + ", got " + match.size());
        runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(type), match, List.of());
    }
    @Override public boolean isOpen(Screen screen) { return screen != null && runtime != null && runtime.getRecipesGui() == screen
                && runtime.getRecipesGui().getParentScreen().isPresent(); }
}
