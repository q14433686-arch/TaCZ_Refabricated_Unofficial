package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.compat.recipeviewer.GunAmmoQueryEntry;
import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;

public class GunAmmoQueryDisplay implements Display {
    private final GunAmmoQueryEntry entry;

    public GunAmmoQueryDisplay(GunAmmoQueryEntry entry) {
        this.entry = entry;
    }

    public GunAmmoQueryEntry getEntry() {
        return entry;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return List.of(EntryIngredients.of(entry.getAmmoStack()));
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(EntryIngredients.of(entry.getGunStack()));
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REIClientPlugin.GUN_AMMO_QUERY;
    }

    @Override public java.util.Optional<net.minecraft.resources.Identifier> getDisplayLocation() { return java.util.Optional.empty(); }
    @Override public me.shedaniel.rei.api.common.display.DisplaySerializer<? extends Display> getSerializer() { return null; }
}
