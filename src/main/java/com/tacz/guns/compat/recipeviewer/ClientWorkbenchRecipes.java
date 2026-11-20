package com.tacz.guns.compat.recipeviewer;

import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Workbench production recipes only (not crafting, or attachment/ammo compatibility pages). */
public final class ClientWorkbenchRecipes {
    private ClientWorkbenchRecipes() {}

    public static Map<Identifier, List<GunSmithTableRecipe>> byTable() {
        // Use the snapshot installed on this client, also on an integrated server. The server's
        // singleton would hide wire-format bugs and can be a different generation during /reload.
        var cache = CommonNetworkCache.INSTANCE;
        var recipes = new ArrayList<GunSmithTableRecipe>();
        for (var entry : cache.getAllTableRecipes()) {
            if (entry.getValue() == null || entry.getValue().getResult() == null) continue;
            var recipe = new GunSmithTableRecipe(entry.getKey(), entry.getValue());
            try {
                recipe.init();
                if (!recipe.getOutput().isEmpty()) recipes.add(recipe);
            } catch (RuntimeException ex) {
                GunMod.LOGGER.error("Skipping invalid workbench recipe {} in recipe viewer", entry.getKey(), ex);
            }
        }

        Map<Identifier, List<GunSmithTableRecipe>> result = new HashMap<>();
        for (var entry : cache.getAllBlocks()) {
            var table = entry.getValue();
            var id = entry.getKey();
            var tabs = table.recipeTabs(id).stream().map(TabConfig::id).collect(Collectors.toSet());
            result.put(id, recipes.stream().filter(r -> tabs.contains(r.getTab()) && table.allowsRecipe(id, r.getId())).toList());
        }
        return result;
    }
}
