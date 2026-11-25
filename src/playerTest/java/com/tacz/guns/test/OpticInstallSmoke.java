package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.TreeMap;

/** Physical optics must have a real host and consume their source stack in survival. The seven
 * hidden OEM optics are not inventory items; their visual coverage belongs to the ADS suite. */
final class OpticInstallSmoke {
    private static final AttachmentType SCOPE = AttachmentType.SCOPE;
    private record Slots(int hand, int source, ItemStack previousHand, ItemStack previousSource) {}

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        var pairs = server.computeOnServer(s -> {
            var hosts = new TreeMap<Identifier, Identifier>(Comparator.comparing(Identifier::toString));
            for (var part : TimelessAPI.getAllCommonAttachmentIndex()) {
                var optic = part.getKey();
                if (!optic.getNamespace().equals("tacz") || part.getValue().getType() != SCOPE
                        || part.getValue().getPojo().isHidden() || part.getValue().getPojo().isBuiltinAttachment()) continue;
                var attachment = AttachmentItemBuilder.create().setId(optic).build();
                var host = TimelessAPI.getAllCommonGunIndex().stream().map(e -> e.getKey())
                        .filter(id -> id.getNamespace().equals("tacz")).sorted(Comparator.comparing(Identifier::toString))
                        .filter(id -> {
                            var stack = GunItemBuilder.create().setId(id).build();
                            var gun = (IGun)stack.getItem();
                            return gun.allowAttachmentType(stack, SCOPE) && gun.allowAttachment(stack, attachment);
                        }).findFirst().orElseThrow(() -> new AssertionError("No direct default host for " + optic));
                hosts.put(optic, host);
            }
            if (hosts.size() != 39) throw new AssertionError("Expected 39 physical default optics, got " + hosts.size());
            return hosts;
        });
        var slots = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            if (player.isCreative()) throw new AssertionError("Expected survival inventory settlement");
            var inventory = player.getInventory();
            int hand = inventory.getSelectedSlot(), source = (hand + 1) % 9;
            return new Slots(hand, source, inventory.getItem(hand).copy(), inventory.getItem(source).copy());
        });
        try {
            for (var pair : pairs.entrySet()) {
                var optic = pair.getKey(); var host = pair.getValue();
                server.runOnServer(s -> {
                    var player = s.getPlayerList().getPlayers().getFirst(); var inv = player.getInventory();
                    inv.setItem(slots.hand(), GunItemBuilder.create().setId(host).build());
                    inv.setItem(slots.source(), AttachmentItemBuilder.create().setId(optic).build());
                    player.inventoryMenu.broadcastChanges();
                });
                ctx.waitFor(mc -> {
                    var held = mc.player.getMainHandItem(); var source = mc.player.getInventory().getItem(slots.source());
                    return held.getItem() instanceof IGun g && host.equals(g.getGunId(held))
                            && source.getItem() instanceof IAttachment a && optic.equals(a.getAttachmentId(source));
                });
                ctx.runOnClient(mc -> {
                    var held = mc.player.getMainHandItem();
                    if (!((IGun)held.getItem()).allowAttachment(held, mc.player.getInventory().getItem(slots.source())))
                        throw new AssertionError("Client rejected " + pair);
                    ClientPlayNetworking.send(new ClientMessageRefitGun(slots.source(), slots.hand(), SCOPE));
                });
                ctx.waitFor(mc -> installed(mc.player.getMainHandItem(), optic) && mc.player.getInventory().getItem(slots.source()).isEmpty());
                server.runOnServer(s -> {
                    var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
                    if (!installed(inv.getSelectedItem(), optic) || !inv.getItem(slots.source()).isEmpty())
                        throw new AssertionError("Wrong authoritative settlement for " + pair);
                });
            }
        } finally {
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst(); var inv = player.getInventory();
                inv.setItem(slots.hand(), slots.previousHand()); inv.setItem(slots.source(), slots.previousSource());
                player.inventoryMenu.broadcastChanges();
            });
        }
        GunMod.LOGGER.info("PORT_OPTIC_INSTALL: {} physical default optics installed through C2S, server and client inventories settled", pairs.size());
    }

    private static boolean installed(ItemStack stack, Identifier optic) {
        return stack.getItem() instanceof IGun gun && optic.equals(gun.getAttachmentId(stack, SCOPE));
    }
}
