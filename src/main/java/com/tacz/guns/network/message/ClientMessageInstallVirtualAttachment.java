package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VirtualOemAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/** A request to select a pack-defined option, never a client-supplied stack. */
public record ClientMessageInstallVirtualAttachment(int slot, AttachmentType attachmentType, Identifier id) implements CustomPacketPayload {
    public static final Type<ClientMessageInstallVirtualAttachment> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_refit_oem"));
    public static final StreamCodec<FriendlyByteBuf, ClientMessageInstallVirtualAttachment> CODEC = StreamCodec.ofMember(
            (msg, buf) -> { buf.writeVarInt(msg.slot); buf.writeEnum(msg.attachmentType); buf.writeIdentifier(msg.id); },
            buf -> new ClientMessageInstallVirtualAttachment(buf.readVarInt(), buf.readEnum(AttachmentType.class), buf.readIdentifier()));

    @Override public Type<ClientMessageInstallVirtualAttachment> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        var inventory = player.getInventory();
        if (slot != inventory.getSelectedSlot() || attachmentType == AttachmentType.NONE) return;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || iGun.hasAttachmentLock(gun) || !iGun.allowAttachmentType(gun, attachmentType)
                || !VirtualOemAttachment.shouldShowIcon(iGun.getGunId(gun), id, attachmentType)) return;
        ItemStack part = AttachmentItemBuilder.create().setId(id).build();
        if (!(part.getItem() instanceof IAttachment attachment) || attachment.getType(part) != attachmentType
                || !iGun.allowAttachment(gun, part) || id.equals(iGun.getAttachmentId(gun, attachmentType))) return;
        ItemStack old = iGun.getAttachment(gun, attachmentType);
        boolean returnOld = !old.isEmpty() && !VirtualOemAttachment.isMarked(old);
        if (returnOld && inventory.getFreeSlot() < 0) return;

        VirtualOemAttachment.mark(part);
        var before = gun.copy();
        iGun.installAttachment(gun, part);
        if (!ItemStack.isSameItemSameComponents(part, iGun.getAttachment(gun, attachmentType))) return;
        if (returnOld && !inventory.add(old)) player.drop(old, false, Prediction.SERVER_ONLY);
        if (attachmentType == AttachmentType.EXTENDED_MAG) iGun.dropAllAmmo(player, gun);
        AttachmentPropertyManager.postChangeEvent(player, gun);
        GunRefitTrigger.INSTANCE.trigger(player, before, gun);
        player.inventoryMenu.broadcastChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
