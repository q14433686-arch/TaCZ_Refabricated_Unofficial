package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.compat.recipeviewer.GunAttachmentQueryEntry;
import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.ArrayList;
import java.util.List;

public class GunAttachmentQueryDisplay implements Display {
    private final GunAttachmentQueryEntry entry;

    public GunAttachmentQueryDisplay(GunAttachmentQueryEntry entry) {
        this.entry = entry;
    }

    public GunAttachmentQueryEntry getEntry() {
        return entry;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        List<EntryIngredient> inputs = new ArrayList<>();
        entry.getAttachmentStacks().forEach(gun -> inputs.add(EntryIngredients.of(gun)));
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(EntryIngredients.of(entry.getGunStack()));
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REIClientPlugin.GUN_ATTACHMENT_QUERY;
    }

    @Override public java.util.Optional<net.minecraft.resources.Identifier> getDisplayLocation() { return java.util.Optional.empty(); }
    @Override public me.shedaniel.rei.api.common.display.DisplaySerializer<? extends Display> getSerializer() { return null; }
}
