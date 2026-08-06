package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.industry.recipe.IndustryProcessDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
import com.tacz.guns.resource.CommonAssetsManager;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;
import java.util.Optional;

/**
 * Live REI view over the synchronized Create process cache.
 *
 * <p>REI constructs plugins before a multiplayer server sends TACZ's common
 * data. Static displays registered at that point are permanently empty. This
 * generator instead resolves the cache when the player asks for recipes or
 * usages, so a configured die and its component route appear immediately after
 * the normal gun-pack synchronization packet arrives.</p>
 */
public final class IndustryProcessDisplayGenerator implements DynamicDisplayGenerator<IndustryProcessDisplay> {
    private final IndustryProcessMachine machine;
    private final CategoryIdentifier<IndustryProcessDisplay> category;

    public IndustryProcessDisplayGenerator(IndustryProcessMachine machine,
                                           CategoryIdentifier<IndustryProcessDisplay> category) {
        this.machine = machine;
        this.category = category;
    }

    private List<IndustryProcessDisplay> current() {
        return CommonAssetsManager.get().getAllIndustryProcesses().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getMachine() == machine)
                .map(entry -> new IndustryProcessDisplay(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public Optional<List<IndustryProcessDisplay>> getRecipeFor(EntryStack<?> entry) {
        return Optional.of(current().stream()
                .filter(display -> display.getOutputEntries().stream()
                        .anyMatch(output -> EntryIngredients.testFuzzy(output, entry)))
                .toList());
    }

    @Override
    public Optional<List<IndustryProcessDisplay>> getUsageFor(EntryStack<?> entry) {
        return Optional.of(current().stream()
                .filter(display -> display.getInputEntries().stream()
                        .anyMatch(input -> EntryIngredients.testFuzzy(input, entry)))
                .toList());
    }

    @Override
    public Optional<List<IndustryProcessDisplay>> generate(ViewSearchBuilder builder) {
        // Keep category browsing useful as well as R/U item lookups. Category
        // generators are only invoked for their own registered category.
        return builder.getCategories().contains(category)
                ? Optional.of(current())
                : Optional.empty();
    }
}
