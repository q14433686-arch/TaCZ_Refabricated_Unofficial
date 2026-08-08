package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.industry.magazine.GunFeedReferenceEntry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;
import java.util.Optional;

/**
 * Live REI lookup for gun/feed relationships.
 *
 * <p>The synchronized gun and feed cache may arrive after REI plugin bootstrap
 * on a dedicated server. Resolve it only when the player opens a category or
 * queries an item so R/U lookups never become a permanent empty snapshot.</p>
 */
public final class GunFeedReferenceDisplayGenerator implements DynamicDisplayGenerator<GunFeedReferenceDisplay> {
    private final CategoryIdentifier<GunFeedReferenceDisplay> category;

    public GunFeedReferenceDisplayGenerator(CategoryIdentifier<GunFeedReferenceDisplay> category) {
        this.category = category;
    }

    private static List<GunFeedReferenceDisplay> current() {
        return GunFeedReferenceEntry.getAll().stream()
                .map(GunFeedReferenceDisplay::new)
                .toList();
    }

    @Override
    public Optional<List<GunFeedReferenceDisplay>> getRecipeFor(EntryStack<?> entry) {
        return Optional.of(current().stream()
                .filter(display -> display.getOutputEntries().stream()
                        .anyMatch(output -> EntryIngredients.testFuzzy(output, entry)))
                .toList());
    }

    @Override
    public Optional<List<GunFeedReferenceDisplay>> getUsageFor(EntryStack<?> entry) {
        return Optional.of(current().stream()
                .filter(display -> display.getInputEntries().stream()
                        .anyMatch(input -> EntryIngredients.testFuzzy(input, entry)))
                .toList());
    }

    @Override
    public Optional<List<GunFeedReferenceDisplay>> generate(ViewSearchBuilder builder) {
        return builder.getCategories().contains(category)
                ? Optional.of(current())
                : Optional.empty();
    }
}
