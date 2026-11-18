package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.util.SlotAdapterHelper;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import java.util.*;

/** All default scopes have a real compatible host; packet installs use no attachment NBT shortcut. */
final class OpticInstallSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var pairs = world.getServer().computeOnServer(s -> {
            Map<Identifier,Identifier> hosts = new TreeMap<>(Comparator.comparing(Identifier::toString));
            for (var part : TimelessAPI.getAllCommonAttachmentIndex()) {
                if (!part.getKey().getNamespace().equals("tacz") || part.getValue().getType()!=AttachmentType.SCOPE
                        || part.getValue().getPojo().isHidden() || part.getValue().getPojo().isBuiltinAttachment()) continue;
                var scope = AttachmentItemBuilder.create().setId(part.getKey()).build();
                for (var entry : TimelessAPI.getAllCommonGunIndex()) {
                    if (!entry.getKey().getNamespace().equals("tacz")) continue;
                    var stack=GunItemBuilder.create().setId(entry.getKey()).build();
                    var gun=(IGun)stack.getItem();
                    if(gun.allowAttachmentType(stack,AttachmentType.SCOPE) && gun.allowAttachment(stack,scope)) {
                        hosts.put(part.getKey(),entry.getKey()); break;
                    }
                }
                if(!hosts.containsKey(part.getKey()))throw new AssertionError("No direct default host for scope "+part.getKey());
            }
            return hosts;
        });
        for(var pair : pairs.entrySet()) {
            int hand=world.getServer().computeOnServer(s->{
                var p=world.getConnection().getServerPlayer();var inv=p.getInventory();int slot=inv.getSelectedSlot();
                inv.setItem(slot,GunItemBuilder.create().setId(pair.getValue()).build());
                inv.setItem((slot+1)%9,AttachmentItemBuilder.create().setId(pair.getKey()).build());
                p.inventoryMenu.broadcastChanges();return slot;
            });
            int source=(hand+1)%9;
            ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun g && pair.getValue().equals(g.getGunId(mc.player.getMainHandItem()))
                    && !mc.player.getInventory().getItem(source).isEmpty());
            ctx.runOnClient(mc->{
                var stack=mc.player.getMainHandItem();var g=(IGun)stack.getItem();
                if(!g.allowAttachment(stack,mc.player.getInventory().getItem(source)))throw new AssertionError("Client rejected "+pair);
                ClientPlayNetworking.send(new ClientMessageRefitGun(source,hand,AttachmentType.SCOPE));
            });
            ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun g && pair.getKey().equals(g.getAttachmentId(mc.player.getMainHandItem(),AttachmentType.SCOPE))
                    && mc.player.getInventory().getItem(source).isEmpty());
        }
        GunMod.LOGGER.info("PORT_OPTIC_INSTALL: {} default scopes found hosts and installed through C2S with inventory settlement",pairs.size());
    }
}
