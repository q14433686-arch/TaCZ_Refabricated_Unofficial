package com.tacz.guns.test;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.AttachmentQueryData;
import com.tacz.guns.compat.recipeviewer.GunAmmoQueryEntry;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.network.message.ClientMessageSelectRefitOption;
import com.tacz.guns.util.VariantHelper;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.DefaultGunAttachments;
import com.tacz.guns.util.ItemNbtUtils;
import com.tacz.guns.util.VirtualOemAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PackServerSmoke {
    @GameTest
    public void loadPacks(GameTestHelper helper) throws IOException {
        var resources = helper.getLevel().getServer().getResourceManager();
        try (var stream = resources.open(Identifier.parse("port_test:probe/version.txt"))) {
            helper.assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim().equals("26.3 overlay"),
                    "Server data came from the wrong gun pack layer");
        }
        helper.assertTrue(TimelessAPI.getCommonGunIndex(Identifier.parse("tacz:ak47")).isPresent(), "Default gun index missing");
        com.tacz.guns.GunMod.LOGGER.info("PORT_SMOKE_SERVER: 26.3 overlay and tacz:ak47 loaded");
        helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(Identifier.parse("port_test:criteria")) != null,
                "Advancement criteria did not register/decode from the pack");
        DefaultCatalogSmoke.server(helper.getLevel().getServer());
        var assets = com.tacz.guns.resource.CommonAssetsManager.getInstance();
        var addedAmmo = Identifier.parse("tacz:300blk");
        helper.assertTrue(TimelessAPI.getCommonAmmoIndex(addedAmmo).orElseThrow().getStackSize() == 50, "Missing new default ammo");
        var ammoRecipe = assets.getTableRecipe(Identifier.parse("tacz:ammo/300blk"));
        ammoRecipe.getResult().init();
        var ammo = ammoRecipe.getResult().getResult();
        helper.assertTrue(ammo.getCount() == 45 && ammo.getItem() instanceof com.tacz.guns.api.item.IAmmo a && a.getAmmoId(ammo).equals(addedAmmo), "Wrong new default ammo recipe");
        var newHost = Identifier.parse("tacz:qr_24");
        var hostIndex = TimelessAPI.getCommonGunIndex(newHost).orElseThrow();
        var recipe = TimelessAPI.getRecipe(Identifier.parse("tacz:gun/qr_24")).orElseThrow().getResult().getResult();
        helper.assertTrue(recipe.getItem() instanceof IGun g && newHost.equals(g.getGunId(recipe))
                && Identifier.parse("tacz:545x39").equals(hostIndex.getGunData().getAmmoId())
                && SlotAdapterHelper.getAvailableSlotAdapters(recipe, AttachmentType.SCOPE).size() == 5, "New default refit chain missing");
        var table = assets.getTableRecipe(Identifier.parse("port_test:condition_true"));
        helper.assertTrue(table != null && assets.getTableRecipe(Identifier.parse("port_test:condition_false")) == null, "Workbenches resurrected a disabled recipe");
        table.getResult().init();
        helper.assertTrue(ItemNbtUtils.getTag(table.getResult().getResult()).getByteOr("fixture", (byte) 0) == 1, "Workbench discarded result components");
        helper.succeed();
    }

    @GameTest
    public void viewerSelectionHonorsTheOpenTable(GameTestHelper helper) {
        var inv = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory();
        var gunId = Identifier.parse("tacz:gun/ak47");
        var menu = new com.tacz.guns.inventory.GunSmithTableMenu(1, inv, com.tacz.guns.api.DefaultAssets.DEFAULT_BLOCK_ID);
        helper.assertTrue(menu.selectFromViewer(gunId, false) && menu.consumeRecipeSelection() == null, "Preview changed the menu");
        helper.assertTrue(menu.selectFromViewer(gunId, true) && gunId.equals(menu.consumeRecipeSelection()) && menu.consumeRecipeSelection() == null,
                "Viewer selection must belong to this menu and be consumed once");
        var ammoTable = new com.tacz.guns.inventory.GunSmithTableMenu(2, inv, Identifier.parse("tacz:ammo_workbench"));
        helper.assertTrue(!ammoTable.selectFromViewer(gunId, true) && ammoTable.consumeRecipeSelection() == null, "Viewer bypassed the table filter");
        helper.succeed();
    }

    @GameTest
    public void bothViewerDirectionsUseTheSameIndexedStacks(GameTestHelper helper) {
        var pairs = GunAmmoQueryEntry.getAllGunAmmoQueryEntries();
        var first = pairs.stream().filter(e -> Identifier.parse("tacz:ak47").equals(
                ((IGun) e.getGunStack().getItem()).getGunId(e.getGunStack()))).findFirst().orElseThrow();
        var pages = AmmoQueryEntry.getAllAmmoQueryEntries(java.util.Collections.nCopies(55, first));
        helper.assertTrue(pages.size() == 2 && pages.getFirst().getGunStacks().size() == 54 && pages.getLast().getGunStacks().size() == 1
                && pages.getLast().getPageNumber() == 2 && pages.getLast().getTotalGunCount() == 55
                && pages.getLast().getAmmoStack() == first.getAmmoStack(), "Ammo reverse pages lost/duplicated entries");
        var parts = AttachmentQueryData.load();
        var reverse = parts.gunAttachmentQueries().getFirst();
        var part = reverse.getAttachmentStacks().getFirst();
        helper.assertTrue(parts.attachmentQueries().stream().anyMatch(e -> e.getAttachmentStack() == part
                        && (e.getAllowGunStacks().contains(reverse.getGunStack()) || e.getExtraAllowGunStacks().contains(reverse.getGunStack()))),
                "Reverse attachment result missing its forward relation");
        helper.assertTrue(parts.compatibilityChecks() <= (long) parts.attachmentCount() * parts.gunCount(), "Rescanned compatibility per direction");
        helper.succeed();
    }

    @GameTest
    public void oemIsNotAnInventoryItem(GameTestHelper helper) {
        var id = Identifier.parse("port_test:oem");
        var scope = Identifier.parse("tacz:scope_acog_ta31");
        var type = AttachmentType.SCOPE;
        var defaults = DefaultGunAttachments.defaults(id);
        helper.assertTrue(scope.equals(defaults.get(type)), "Missing pack default");
        var gun = GunItemBuilder.create().setId(id).putAllVirtualAttachment(defaults).build();
        // Exercise the 26.3 component codec, not just the JSON adapter.
        gun = ItemNbtUtils.loadItemStack(ItemNbtUtils.saveItemStack(gun));
        var item = (IGun) gun.getItem();
        helper.assertTrue(VirtualOemAttachment.isMarked(item.getAttachment(gun, type)), "Lost virtual flag on save/load");
        item.unloadAttachment(gun, type);
        helper.assertTrue(item.getAttachment(gun, type).isEmpty(), "Virtual slot did not clear");
        helper.assertTrue(item.getBuiltInAttachmentId(gun, type).equals(Identifier.parse("tacz:sight_sro_dot")), "Missing visual fallback");
        var explicit = GunItemBuilder.create().setId(id).putAllVirtualAttachment(defaults).putAttachment(type, scope).build();
        var installed = ((IGun) explicit.getItem()).getAttachment(explicit, type);
        helper.assertTrue(!installed.isEmpty() && !VirtualOemAttachment.isMarked(installed), "Explicit recipe item must win over OEM");
        helper.succeed();
    }

    @GameTest
    public void refitOptionsPersistAndRejectIncompatibleTransitions(GameTestHelper helper) {
        var id = Identifier.parse("port_test:oem");
        var gun = GunItemBuilder.create().setId(id).build();
        var item = (IGun) gun.getItem();
        var type = AttachmentType.SCOPE;
        var part = AttachmentItemBuilder.create().setId(Identifier.parse("tacz:sight_deltapoint_rifle")).build();
        var adapter = new ClientMessageSelectRefitOption(0, type, ClientMessageSelectRefitOption.Kind.ADAPTER, Identifier.parse("port_test:mount"));
        helper.assertTrue(!item.allowAttachment(gun, part), "Adapter-only part allowed directly");
        helper.assertTrue(adapter.apply(gun) && item.allowAttachment(gun, part), "Nested tag adapter did not enable part");
        item.installAttachment(gun, part);
        helper.assertTrue(!new ClientMessageSelectRefitOption(0, type, adapter.kind(), null).apply(gun), "Must unload before removing the required mount");
        var optic = Identifier.parse("port_test:optic");
        var variant = new ClientMessageSelectRefitOption(0, type, ClientMessageSelectRefitOption.Kind.VARIANT, optic);
        helper.assertTrue(variant.apply(gun), "Variant target tag not honored");
        var saved = ItemNbtUtils.loadItemStack(ItemNbtUtils.saveItemStack(gun));
        helper.assertTrue(optic.equals(VariantHelper.getVariant(item.getAttachment(saved, type))), "Lost nested variant");
        helper.assertTrue(adapter.selection().equals(SlotAdapterHelper.getSlotAdapter(saved, type)), "Lost slot adapter");
        helper.assertTrue(!new ClientMessageSelectRefitOption(0, type, variant.kind(), id).apply(saved), "Unknown variant accepted");
        helper.succeed();
    }
}
