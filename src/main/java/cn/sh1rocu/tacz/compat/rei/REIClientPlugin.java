package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.category.AmmoQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunAmmoQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunAttachmentQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.AttachmentQueryCategory;
import cn.sh1rocu.tacz.compat.rei.category.GunSmithTableCategory;
import cn.sh1rocu.tacz.compat.rei.display.AmmoQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunAmmoQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunAttachmentQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.AttachmentQueryDisplay;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import cn.sh1rocu.tacz.compat.rei.entry.AttachmentQueryEntry;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.AttachmentQueryData;
import com.tacz.guns.compat.recipeviewer.GunAmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.ClientWorkbenchRecipes;
import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import me.shedaniel.rei.api.common.display.Display;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;

public class REIClientPlugin implements me.shedaniel.rei.api.client.plugins.REIClientPlugin {
    public static final CategoryIdentifier<AttachmentQueryDisplay> ATTACHMENT_QUERY =
            CategoryIdentifier.of(GunMod.MOD_ID, "plugins/attachment_query");
    public static final CategoryIdentifier<AmmoQueryDisplay> AMMO_QUERY =
            CategoryIdentifier.of(GunMod.MOD_ID, "plugins/ammo_query");

    public static final CategoryIdentifier<GunAmmoQueryDisplay> GUN_AMMO_QUERY = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/gun_ammo_query");
    public static final CategoryIdentifier<GunAttachmentQueryDisplay> GUN_ATTACHMENT_QUERY = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/gun_attachment_query");

    static final Object PACK_ORIGIN = new Object();

    @Override
    public void registerCategories(CategoryRegistry registry) {
        // The category API has no unregister. A table removed by a later pack sync has neither a
        // visible tab nor recipes; the next natural REI registration drops the empty key entirely.
        registry.registerVisibilityPredicate(category -> category instanceof GunSmithTableCategory table
                && CommonNetworkCache.INSTANCE.getBlockIndex(table.table()) == null ? EventResult.interruptFalse() : EventResult.pass());
        registerTables(registry);
        registry.add(new AttachmentQueryCategory());
        registry.add(new AmmoQueryCategory());
        registry.add(new GunAmmoQueryCategory());
        registry.add(new GunAttachmentQueryCategory());
    }

    static Map<Identifier, CategoryIdentifier<GunSmithTableDisplay>> registerTables(CategoryRegistry registry) {
        Map<Identifier, CategoryIdentifier<GunSmithTableDisplay>> displays = new HashMap<>();
        var map = CommonNetworkCache.INSTANCE.getAllBlocks();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            CategoryIdentifier<GunSmithTableDisplay> id = CategoryIdentifier.of(GunMod.MOD_ID, "plugins/gun_smith_table/" + entry.getKey().toString().replace(':', '_'));
            var existing = registry.tryGet(id).map(CategoryRegistry.CategoryConfiguration::getCategory).orElse(null);
            var title = item.getName(icon);
            if (existing == null || existing instanceof GunSmithTableCategory table && !table.represents(title, icon)) {
                registry.add(new GunSmithTableCategory(entry.getKey(), title, icon, id));
                registry.addWorkstations(id, EntryStacks.of(icon));
            }
            displays.put(entry.getKey(), id);
        }
        return displays;
    }

    @Override
    public void registerEntries(EntryRegistry entries) {
        ReiPackRefresh.rememberVariants();
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new GunSmithTableTransferHandler());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        if (Minecraft.getInstance().level == null) return;
        collect(registerTables(CategoryRegistry.getInstance()), d -> registry.add(d, PACK_ORIGIN));
    }

    /** Only displays authored here get PACK_ORIGIN (never vanilla crafting, even when its output is a TaCZ item). */
    static void collect(Map<Identifier, CategoryIdentifier<GunSmithTableDisplay>> displays, Consumer<Display> add) {
        ClientWorkbenchRecipes.byTable().forEach((table, recipes) -> {
            var category = displays.get(table);
            if (category != null) recipes.forEach(recipe -> add.accept(new GunSmithTableDisplay(recipe, Map.entry(table, category))));
        });

        var attachments = AttachmentQueryData.load();
        attachments.attachmentQueries().forEach(entry -> add.accept(new AttachmentQueryDisplay(new AttachmentQueryEntry(entry))));
        attachments.gunAttachmentQueries().forEach(entry -> add.accept(new GunAttachmentQueryDisplay(entry)));
        var ammo = GunAmmoQueryEntry.getAllGunAmmoQueryEntries();
        ammo.forEach(entry -> add.accept(new GunAmmoQueryDisplay(entry)));
        AmmoQueryEntry.getAllAmmoQueryEntries(ammo).forEach(entry -> add.accept(new AmmoQueryDisplay(entry)));
    }
}
