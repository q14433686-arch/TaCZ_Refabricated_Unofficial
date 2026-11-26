package com.tacz.probe;

import com.tacz.guns.compat.jei.category.GunAmmoQueryCategory;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.init.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/** Observes JEI's installed runtime, never registers a display or invokes TaCZ's plugin. */
public final class JeiProbe implements IModPlugin, ViewerProbe {
    private static final Identifier AMMO = Identifier.parse("tacz:gun_smith_table/tacz_ammo_workbench");
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
        return recipes.getRecipeType(AMMO, GunSmithTableRecipe.class)
                .map(type -> recipes.createRecipeLookup(type).get().anyMatch(r -> id.equals(r.getId()))).orElse(false);
    }
    public void checkInstalled(ClientGameTestContext ctx, Identifier live) {
        ctx.runOnClient(mc -> {
            var helper = runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
            ViewerProbe.checkTableKeys(s -> helper.getUid(s, UidContext.Recipe));
        });
    }
    public boolean select(GunSmithTableScreen screen, Identifier id, boolean commit) {
        var recipes = runtime.getRecipeManager();
        var type = recipes.getRecipeType(AMMO, GunSmithTableRecipe.class).orElseThrow();
        var category = recipes.getRecipeCategory(type);
        var recipe = recipes.createRecipeLookup(type).get().filter(r -> id.equals(r.getId())).findFirst().orElseThrow();
        var layout = recipes.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).orElseThrow();
        return handler.transferRecipe(screen.getMenu(), recipe, layout.getRecipeSlotsView(), Minecraft.getInstance().player, false, commit) == null;
    }
    public boolean hasOrdinaryCrafting() {
        return runtime != null && runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(r -> r.id().identifier().equals(Identifier.parse("tacz:gun_smith_table")))
                .flatMap(r -> r.value().display().stream())
                .flatMap(d -> d.result().resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level)).stream())
                .anyMatch(s -> s.is(ModItems.GUN_SMITH_TABLE));
    }
}
