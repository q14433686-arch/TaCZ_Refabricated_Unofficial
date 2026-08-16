package cn.sh1rocu.tacz.compat.rei.display;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adapts the viewer-neutral ammo query data to an REI display. */
public final class AmmoQueryDisplay implements Display {
    private final AmmoQueryEntry entry;

    public AmmoQueryDisplay(AmmoQueryEntry entry) {
        this.entry = entry;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        List<EntryIngredient> inputs = new ArrayList<>();
        entry.getGunStacks().forEach(gun -> inputs.add(EntryIngredients.of(gun)));
        if (!entry.getExtraGunStacks().isEmpty()) {
            inputs.add(EntryIngredients.ofItemStacks(entry.getExtraGunStacks()));
        }
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(EntryIngredients.of(entry.getAmmoStack()));
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REIClientPlugin.AMMO_QUERY;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.empty();
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
}
