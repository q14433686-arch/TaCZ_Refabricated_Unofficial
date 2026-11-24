package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.network.WorkbenchRecipeCounts;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.item.GunSmithTableItem;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.impl.client.registry.display.DisplayRegistryImpl;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Optional REI 26.1.819 adapter. A gun-pack packet is NOT a server recipe update. */
public final class ReiPackRefresh {
    private static volatile Set<EntryStack<?>> previousVariants = Set.of();

    /** REI's ordinary registration collects creative stacks; remember only our pack-backed variants. */
    static void rememberVariants() { previousVariants = variants(); }

    private static Set<EntryStack<?>> variants() {
        Set<EntryStack<?>> variants = new LinkedHashSet<>();
        Consumer<ItemStack> add = stack -> variants.add(EntryStacks.of(stack));
        for (var type : GunTabType.values()) AbstractGunItem.fillItemCategory(type).forEach(add);
        for (var type : AttachmentType.values()) if (type != AttachmentType.NONE) AttachmentItem.fillItemCategory(type).forEach(add);
        AmmoItem.fillItemCategory().forEach(add);
        GunSmithTableItem.fillItemCategory().forEach(add);
        return variants;
    }

    public static boolean refreshWhenReady() {
        // The same idle barrier REI uses before applying its own display-sync jobs. Don't touch a
        // holder between stages or while an async reload is queued but not yet executing a plugin.
        if (PluginManager.areAnyReloading() || ReloadManagerImpl.countRunningReloadTasks() != 0) return false;
        var categories = CategoryRegistry.getInstance();
        // No initial registration yet (or our plugin is disabled). A natural pass reads the
        // already-installed cache; polling forever or preempting it would do more harm than good.
        if (categories.tryGet(REIClientPlugin.AMMO_QUERY).isEmpty()) return true;
        var registry = (DisplayRegistryImpl) DisplayRegistry.getInstance();
        var newDisplays = new ArrayList<Display>();
        REIClientPlugin.collect(REIClientPlugin.registerTables(categories), newDisplays::add);
        // The public API has add but no remove. Use the pinned holder's remove, not mutations of
        // getAll(): this also updates the category, display key, origin and input/output indexes.
        var owned = registry.getAll().values().stream().flatMap(java.util.Collection::stream)
                .filter(d -> registry.getDisplayOrigin(d) == REIClientPlugin.PACK_ORIGIN).toList();
        owned.forEach(registry.holder()::remove);
        newDisplays.forEach(d -> registry.add(d, REIClientPlugin.PACK_ORIGIN));

        var entries = EntryRegistry.getInstance();
        var current = variants();
        var removed = new LinkedHashSet<>(previousVariants);
        removed.removeAll(current);
        if (!removed.isEmpty()) entries.removeEntryIf(removed::contains);
        entries.addEntries(current.stream().filter(e -> !entries.alreadyContain(e)).toList());
        previousVariants = current;
        var tableRecipes = newDisplays.stream().filter(GunSmithTableDisplay.class::isInstance)
                .map(d -> ((GunSmithTableDisplay) d).getRecipe().getId()).collect(Collectors.toSet());
        GunMod.LOGGER.info("[TACZ Recipe Viewer] Replaced {} pack displays with {}; distinct tacz workbench recipes displayed: {}; server crafting displays left in place.",
                owned.size(), newDisplays.size(), WorkbenchRecipeCounts.inTaczNamespace(tableRecipes));
        return true;
    }

    private ReiPackRefresh() {}
}
