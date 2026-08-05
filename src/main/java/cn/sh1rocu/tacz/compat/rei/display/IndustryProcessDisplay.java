package cn.sh1rocu.tacz.compat.rei.display;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.industry.recipe.IndustryProcessDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/** REI view of a data-synchronised TACZ Create Fly process. */
public final class IndustryProcessDisplay implements Display {
    private final Identifier id;
    private final IndustryProcessDefinition process;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public IndustryProcessDisplay(Identifier id, IndustryProcessDefinition process) {
        this.id = id;
        this.process = process;
        this.inputs = process.getInputs().stream()
                .map(spec -> spec.isTag()
                        ? EntryIngredients.ofItemTag(TagKey.create(Registries.ITEM, spec.getItemId()))
                        : EntryIngredients.of(spec.createStack()))
                .toList();
        this.outputs = process.getOutputs().stream()
                .map(spec -> EntryIngredients.of(spec.createStack()))
                .toList();
    }

    public IndustryProcessDefinition getProcess() {
        return process;
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
        return REIClientPlugin.getIndustryCategory(process.getMachine());
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(id);
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        // Process data is synchronised by TACZ, not serialised by REI.
        return null;
    }
}
