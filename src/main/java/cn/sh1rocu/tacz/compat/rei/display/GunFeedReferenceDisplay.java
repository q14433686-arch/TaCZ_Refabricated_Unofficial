package cn.sh1rocu.tacz.compat.rei.display;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.industry.magazine.GunFeedReferenceEntry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** REI projection of one read-only {@link GunFeedReferenceEntry}. */
public final class GunFeedReferenceDisplay implements Display {
    private final GunFeedReferenceEntry entry;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public GunFeedReferenceDisplay(GunFeedReferenceEntry entry) {
        this.entry = entry;
        List<EntryIngredient> accepted = new ArrayList<>();
        accepted.add(EntryIngredients.ofItemStacks(entry.getAmmoStacks()));
        if (entry.hasDeclaredPhysicalCarrier()) {
            accepted.add(EntryIngredients.ofItemStacks(entry.getCarrierStacks()));
        }
        this.inputs = List.copyOf(accepted);
        this.outputs = List.of(EntryIngredients.of(entry.getGunStack()));
    }

    public GunFeedReferenceEntry getEntry() {
        return entry;
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
        return REIClientPlugin.GUN_FEED_REFERENCE;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(entry.getGunId());
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        // The reference is built lazily from TACZ's synchronized data cache.
        return null;
    }
}
