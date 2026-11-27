package com.tacz.probe;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.network.message.ClientMessageSelectRefitOption;
import com.tacz.guns.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

public class RefitChecks {
    public static void oemIsNotAnInventoryItem(GameTestHelper helper) {
        var id = Identifier.parse("port_test:oem");
        var scope = Identifier.parse("tacz:scope_acog_ta31");
        var type = AttachmentType.SCOPE;
        var defaults = DefaultGunAttachments.defaults(id);
        CatalogChecks.require(scope.equals(defaults.get(type)), "Missing pack default");
        var gun = GunItemBuilder.create().setId(id).putAllVirtualAttachment(defaults).build();
        // Exercise the 1.21.11 component codec, not just the JSON adapter.
        gun = ItemNbtUtils.loadItemStack(ItemNbtUtils.saveItemStack(gun));
        var item = (IGun) gun.getItem();
        CatalogChecks.require(VirtualOemAttachment.isMarked(item.getAttachment(gun, type)), "Lost virtual flag on save/load");
        item.unloadAttachment(gun, type);
        CatalogChecks.require(item.getAttachment(gun, type).isEmpty(), "Virtual slot did not clear");
        CatalogChecks.require(item.getBuiltInAttachmentId(gun, type).equals(Identifier.parse("tacz:sight_sro_dot")), "Missing visual fallback");
        var explicit = GunItemBuilder.create().setId(id).putAllVirtualAttachment(defaults).putAttachment(type, scope).build();
        var installed = ((IGun) explicit.getItem()).getAttachment(explicit, type);
        CatalogChecks.require(!installed.isEmpty() && !VirtualOemAttachment.isMarked(installed), "Explicit recipe item must win over OEM");
        helper.succeed();
    }

    public static void refitOptionsPersistAndRejectIncompatibleTransitions(GameTestHelper helper) {
        var id = Identifier.parse("port_test:oem");
        var gun = GunItemBuilder.create().setId(id).build();
        var item = (IGun) gun.getItem();
        var type = AttachmentType.SCOPE;
        var part = AttachmentItemBuilder.create().setId(Identifier.parse("tacz:sight_deltapoint_rifle")).build();
        part.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
        var adapter = new ClientMessageSelectRefitOption(0, type, ClientMessageSelectRefitOption.Kind.ADAPTER, Identifier.parse("port_test:mount"));
        CatalogChecks.require(!item.allowAttachment(gun, part), "Adapter-only part allowed directly");
        CatalogChecks.require(adapter.apply(gun) && item.allowAttachment(gun, part), "Nested tag adapter did not enable part");
        item.installAttachment(gun, part);
        CatalogChecks.require(!new ClientMessageSelectRefitOption(0, type, adapter.kind(), null).apply(gun), "Must unload before removing the required mount");
        var optic = Identifier.parse("port_test:optic");
        var variant = new ClientMessageSelectRefitOption(0, type, ClientMessageSelectRefitOption.Kind.VARIANT, optic);
        CatalogChecks.require(variant.apply(gun), "Variant target tag not honored");
        var saved = ItemNbtUtils.loadItemStack(ItemNbtUtils.saveItemStack(gun));
        CatalogChecks.require(item.getAttachment(saved, type).isEnchanted(), "Nested part lost a world-registry component");
        CatalogChecks.require(optic.equals(VariantHelper.getVariant(item.getAttachment(saved, type))), "Lost nested variant");
        CatalogChecks.require(adapter.selection().equals(SlotAdapterHelper.getSlotAdapter(saved, type)), "Lost slot adapter");
        CatalogChecks.require(!new ClientMessageSelectRefitOption(0, type, variant.kind(), id).apply(saved), "Unknown variant accepted");
        helper.succeed();
    }
}
