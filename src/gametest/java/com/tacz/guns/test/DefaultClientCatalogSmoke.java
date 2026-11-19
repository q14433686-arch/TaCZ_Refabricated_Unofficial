package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientIndexManager;
import net.minecraft.resources.Identifier;

final class DefaultClientCatalogSmoke {
    static void client() {
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientGunIndex(), 70, "client gun");
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientAmmoIndex(), 25, "client ammo");
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientAttachmentIndex(), 126, "client attachment");
        DefaultCatalogSmoke.catalog(ClientIndexManager.getAllBlocks(), 3, "client block");
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientSlotAdapterIndex(), 5, "client adapter");
        checkPortIndexes();
        for (var entry : TimelessAPI.getAllClientGunIndex()) {
            if (!entry.getKey().getNamespace().equals("tacz")) continue;
            var display = entry.getValue().getDefaultDisplay();
            if (display == null || display.getAnimationStateMachine() == null || display.getGunModel() == null)
                throw new AssertionError("Unusable default model or animation: " + entry.getKey());
        }
        var display = TimelessAPI.getClientGunIndex(Identifier.parse("tacz:m1897")).orElseThrow().getDefaultDisplay();
        if (display.getShellModel() == null || !Identifier.parse("tacz:textures/shell/12g_copper_shell.png").equals(display.getShellTextureLocation()))
            throw new AssertionError("Gun-specific shell was replaced by the generic ammo shell");
        GunMod.LOGGER.info("PORT_CATALOG: client loaded 229 indexes, all 70 first-person rigs/state machines and the shell override");
    }

    static void checkPortIndexes() {
        var variants = TimelessAPI.getAllClientAttachmentVariantIndex();
        if (variants.isEmpty() || TimelessAPI.getAllClientSlotAdapterIndex().isEmpty()) throw new AssertionError("No public client indexes");
        variants.forEach(e -> { if (TimelessAPI.getClientAttachmentVariantIndex(e.getKey()).orElseThrow() != e.getValue()) throw new AssertionError(e); });
        TimelessAPI.getAllClientSlotAdapterIndex().forEach(e -> { if (TimelessAPI.getClientSlotAdapterIndex(e.getKey()).orElseThrow() != e.getValue()) throw new AssertionError(e); });
        var range = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:scope_xm157")).orElseThrow();
        var counter = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:scope_mk5hd")).orElseThrow();
        if (!range.hasRangefinder() || !range.hasImpactPrediction() || !counter.hasAmmoCounter() || counter.hasRangefinder())
            throw new AssertionError("Incorrect optic capability hints");
        var rifle = com.tacz.guns.api.item.gun.AbstractGunItem.fillItemCategory(com.tacz.guns.api.item.GunTabType.RIFLE);
        checkOrder(rifle.stream().map(s -> ((com.tacz.guns.api.item.IGun)s.getItem()).getGunId(s)).toList());
        checkOrder(com.tacz.guns.item.AttachmentItem.fillItemCategory(com.tacz.guns.api.item.attachment.AttachmentType.SCOPE).stream()
                .map(s -> ((com.tacz.guns.api.item.IAttachment)s.getItem()).getAttachmentId(s)).toList());
        checkOrder(com.tacz.guns.item.AmmoItem.fillItemCategory().stream().map(s -> ((com.tacz.guns.api.item.IAmmo)s.getItem()).getAmmoId(s)).toList());
        checkOrder(com.tacz.guns.item.GunSmithTableItem.fillItemCategory().stream()
                .map(s -> ((com.tacz.guns.api.item.nbt.BlockItemDataAccessor)s.getItem()).getBlockId(s)).toList());
    }
    private static void checkOrder(java.util.List<Identifier> ids) {
        if (!ids.getFirst().getNamespace().equals("tacz")) throw new AssertionError("Default pack not first: " + ids);
        String prev = "tacz";
        for (var id : ids) {
            var next = id.getNamespace();
            if (!prev.equals("tacz") && (next.equals("tacz") || prev.compareTo(next)>0)) throw new AssertionError("Out of order: " + ids);
            prev = next;
        }
    }
}
