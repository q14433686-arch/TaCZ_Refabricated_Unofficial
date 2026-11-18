package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageSelectRefitOption;
import com.tacz.guns.util.SlotAdapterHelper;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Exercise production requests, inventory settlement and the new pack's real Lua/mesh paths. */
final class DefaultRefitSmoke {
    private static final Identifier HOST = Identifier.parse("tacz:qr_24");
    private static final Identifier OPTIC = Identifier.parse("tacz:sight_sro_dot");
    private static final Identifier RISER = Identifier.parse("tacz:riser_pistol_low");
    private static final Identifier GRIP = Identifier.parse("tacz:grip_troy");
    private static final AttachmentType SCOPE = AttachmentType.SCOPE;

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        int hand = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var inv = player.getInventory();
            int slot = inv.getSelectedSlot();
            // Use the actual recipe products; no client-side stack edits or call to packet.apply().
            var gun = recipe("gun/qr_24");
            var grip = recipe("attachments/grip_troy");
            if (!isHost(gun) || !(grip.getItem() instanceof IAttachment part) || !part.getAttachmentId(grip).equals(GRIP)
                    || !(recipe("attachments/grip_magpul_afg").getItem() instanceof IAttachment)) throw new AssertionError("Bad migrated workbench products");
            if (SlotAdapterHelper.getAvailableSlotAdapters(gun, SCOPE).size() != 5 || SlotAdapterHelper.allowsDirectAttachment(gun, OPTIC))
                throw new AssertionError("Wrong default adapter tag closure");
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            inv.setItem((slot + 1) % 9, AttachmentItemBuilder.create().setId(OPTIC).build());
            inv.setItem((slot + 2) % 9, grip);
            player.inventoryMenu.broadcastChanges();
            return slot;
        });
        int sightSlot = (hand + 1) % 9, gripSlot = (hand + 2) % 9;
        context.waitFor(mc -> isHost(mc.player.getMainHandItem()) && mc.player.getInventory().getItem(sightSlot).getItem() instanceof IAttachment);
        context.runOnClient(mc -> {
            var display = TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow();
            if (display.getAnimationStateMachine() == null || display.getAdsPath() == null || display.getGunModel() == null)
                throw new AssertionError("New default state machine/rig/ADS missing");
            var adapter = TimelessAPI.getClientSlotAdapterIndex(RISER).orElseThrow();
            if (adapter.getAdapterModel() == null || adapter.getMountOffset() == null || adapter.getMountOffset().y != .375f)
                throw new AssertionError("Wrong riser model/offset");
            // Second request is also an ordering fence: if the first were accepted the inventory slot would be empty.
            ClientPlayNetworking.send(new ClientMessageRefitGun(sightSlot, hand, SCOPE));
            ClientPlayNetworking.send(select(hand, RISER));
        });
        context.waitFor(mc -> RISER.equals(SlotAdapterHelper.getSlotAdapter(mc.player.getMainHandItem(), SCOPE)));
        context.runOnClient(mc -> {
            if (mc.player.getInventory().getItem(sightSlot).isEmpty()) throw new AssertionError("Incompatible install consumed the sight");
            ClientPlayNetworking.send(new ClientMessageRefitGun(sightSlot, hand, SCOPE));
        });
        context.waitFor(mc -> installed(mc.player.getMainHandItem(), SCOPE, OPTIC) && mc.player.getInventory().getItem(sightSlot).isEmpty());
        context.runOnClient(mc -> {
            ClientPlayNetworking.send(select(hand, null)); // removing the required riser is not legal now
            ClientPlayNetworking.send(new ClientMessageRefitGun(gripSlot, hand, AttachmentType.GRIP));
        });
        context.waitFor(mc -> installed(mc.player.getMainHandItem(), AttachmentType.GRIP, GRIP) && mc.player.getInventory().getItem(gripSlot).isEmpty());
        world.getServer().runOnServer(server -> {
            var held = world.getConnection().getServerPlayer().getMainHandItem();
            if (!installed(held, SCOPE, OPTIC) || !RISER.equals(SlotAdapterHelper.getEffectiveSlotAdapter(held, SCOPE, OPTIC)))
                throw new AssertionError("Removal detached a required mount");
        });
        context.waitTicks(20);
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: default QR-24 recipe, Lua, riser, restricted refit and replicated inventory passed");
    }

    private static ItemStack recipe(String path) {
        return TimelessAPI.getRecipe(Identifier.fromNamespaceAndPath("tacz", path)).orElseThrow().getResult().getResult().copy();
    }

    private static boolean isHost(ItemStack stack) {
        return stack.getItem() instanceof IGun gun && HOST.equals(gun.getGunId(stack));
    }

    private static boolean installed(ItemStack stack, AttachmentType type, Identifier id) {
        return isHost(stack) && id.equals(((IGun) stack.getItem()).getAttachmentId(stack, type));
    }

    private static ClientMessageSelectRefitOption select(int slot, Identifier id) {
        return new ClientMessageSelectRefitOption(slot, SCOPE, ClientMessageSelectRefitOption.Kind.ADAPTER, id);
    }
}
