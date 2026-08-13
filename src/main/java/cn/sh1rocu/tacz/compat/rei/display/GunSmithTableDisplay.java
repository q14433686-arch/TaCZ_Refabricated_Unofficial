package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GunSmithTableDisplay extends BasicDisplay {
    private final GunSmithTableRecipe recipe;
    private final Map.Entry<Identifier, CategoryIdentifier<GunSmithTableDisplay>> entry;

    public GunSmithTableDisplay(GunSmithTableRecipe recipe, Map.Entry<Identifier, CategoryIdentifier<GunSmithTableDisplay>> entry) {
        super(toEntryIngredients(recipe.getInputs()),
                Collections.singletonList(EntryIngredients.of(recipe.getOutput())), Optional.ofNullable(entry.getKey()));
        this.recipe = recipe;
        this.entry = entry;
    }

    /**
     * A malformed or currently unavailable third-party tag must not abort REI's whole display
     * registration. {@code EntryIngredients.ofIngredients} dereferences every Ingredient eagerly;
     * TACZ intentionally resolves old pack ingredients lazily, so represent an unresolved slot as
     * empty and keep all later recipes/categories visible.
     */
    private static List<EntryIngredient> toEntryIngredients(List<GunSmithTableIngredient> ingredients) {
        List<EntryIngredient> entries = new ArrayList<>(ingredients.size());
        for (GunSmithTableIngredient ingredient : ingredients) {
            net.minecraft.world.item.crafting.Ingredient resolved = ingredient.getIngredient();
            entries.add(resolved == null ? EntryIngredient.empty() : EntryIngredients.ofIngredient(resolved));
        }
        return entries;
    }

    public GunSmithTableRecipe getRecipe() {
        return recipe;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return entry.getValue();
    }


    @Override
    public DisplaySerializer<? extends GunSmithTableDisplay> getSerializer() {
        return null;
    }
}
