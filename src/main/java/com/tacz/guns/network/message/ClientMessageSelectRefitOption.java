package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ClientMessageSelectRefitOption(int gunSlot, AttachmentType slot, Kind kind, @Nullable Identifier selection) implements CustomPacketPayload {
    public enum Kind { VARIANT, ADAPTER }
    public static final Type<ClientMessageSelectRefitOption> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_select_refit_option"));
    public static final StreamCodec<FriendlyByteBuf, ClientMessageSelectRefitOption> CODEC = StreamCodec.ofMember(
            (msg, buf) -> { buf.writeVarInt(msg.gunSlot); buf.writeEnum(msg.slot); buf.writeEnum(msg.kind);
                buf.writeBoolean(msg.selection != null); if (msg.selection != null) buf.writeIdentifier(msg.selection); },
            buf -> new ClientMessageSelectRefitOption(buf.readVarInt(), buf.readEnum(AttachmentType.class), buf.readEnum(Kind.class), buf.readBoolean() ? buf.readIdentifier() : null));

    @Override public Type<ClientMessageSelectRefitOption> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        if (gunSlot != player.getInventory().getSelectedSlot()) return;
        var stack = player.getMainHandItem();
        var before = stack.copy();
        if (!apply(stack)) return;
        AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
        GunRefitTrigger.INSTANCE.trigger(player, before, stack);
        player.inventoryMenu.broadcastChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }

    /** Validate against the server's current stack and indexes, including the mount that would remain after removal. */
    public boolean apply(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || gun.hasAttachmentLock(stack) || slot == AttachmentType.NONE || !gun.allowAttachmentType(stack, slot)) return false;
        ItemStack part = gun.getAttachment(stack, slot);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(part);
        if (!part.isEmpty() && (attachment == null || attachment.getType(part) != slot)) return false;
        Identifier id = attachment == null ? null : attachment.getAttachmentId(part);
        if (kind == Kind.VARIANT) {
            if (id == null || !gun.allowAttachment(stack, part)) return false;
            if (selection == null) VariantHelper.removeVariant(part);
            else {
                if (TimelessAPI.getCommonAttachmentVariantIndex(selection).filter(index -> index.appliesTo(id)).isEmpty()) return false;
                VariantHelper.setVariant(part, selection);
            }
            gun.installAttachment(stack, part);
        } else if (selection == null) {
            if (id != null && !SlotAdapterHelper.allowsDirectAttachment(stack, id)) return false;
            SlotAdapterHelper.removeSlotAdapter(stack, slot);
        } else {
            if (SlotAdapterHelper.getAvailableSlotAdapters(stack, slot).stream().noneMatch(entry -> entry.getKey().equals(selection)
                    && (id == null || entry.getValue().allowsAttachment(id)))) return false;
            SlotAdapterHelper.setSlotAdapter(stack, slot, selection);
        }
        return true;
    }
}
