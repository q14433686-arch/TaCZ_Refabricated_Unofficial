package com.tacz.guns.test;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
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
