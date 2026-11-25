package com.tacz.probe;

import com.tacz.guns.compat.jei.category.GunAmmoQueryCategory;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/** Observes JEI's installed runtime, never registers a display or invokes TaCZ's plugin. */
public final class JeiProbe implements IModPlugin, ViewerProbe {
    private static IJeiRuntime runtime;
    public Identifier getPluginUid() { return Identifier.parse("tacz_packaged_probe:observer"); }
    public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    public void onRuntimeUnavailable() { runtime = null; }
    public boolean hasGun(Identifier id) {
        return runtime != null && runtime.getRecipeManager().createRecipeLookup(GunAmmoQueryCategory.GUN_AMMO_QUERY).get()
                .anyMatch(e -> ViewerProbe.gun(e.getGunStack(), id));
    }
    public boolean hasWorkbenchRecipe(Identifier id) {
        if (runtime == null) return false;
        var recipes = runtime.getRecipeManager();
        return recipes.getRecipeType(Identifier.parse("tacz:gun_smith_table/tacz_ammo_workbench"), GunSmithTableRecipe.class)
                .map(type -> recipes.createRecipeLookup(type).get().anyMatch(r -> id.equals(r.getId()))).orElse(false);
    }
    public boolean hasOrdinaryCrafting() {
        return runtime != null && runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                .anyMatch(r -> r.id().identifier().equals(Identifier.parse("tacz:gun_smith_table")));
    }
}
