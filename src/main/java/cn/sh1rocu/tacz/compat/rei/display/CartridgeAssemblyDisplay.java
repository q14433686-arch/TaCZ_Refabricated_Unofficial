package cn.sh1rocu.tacz.compat.rei.display;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/** REI representation of the dedicated four-slot cartridge assembly GUI. */
public final class CartridgeAssemblyDisplay implements Display {
    private final Identifier id;
    private final CartridgeAssemblyDefinition definition;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public CartridgeAssemblyDisplay(Identifier id, CartridgeAssemblyDefinition definition) {
        this.id = id;
        this.definition = definition;
        this.inputs = List.of(
                EntryIngredients.of(definition.createCasePreview()),
                EntryIngredients.of(definition.createProjectilePreview()),
                EntryIngredients.of(definition.createPrimerPreview()),
                EntryIngredients.of(definition.createPropellantPreview())
        );
        this.outputs = List.of(EntryIngredients.of(definition.createResult()));
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REIClientPlugin.CARTRIDGE_ASSEMBLY;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(id);
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        // Definitions are synchronized by TACZ's common data cache.
        return null;
    }
}
