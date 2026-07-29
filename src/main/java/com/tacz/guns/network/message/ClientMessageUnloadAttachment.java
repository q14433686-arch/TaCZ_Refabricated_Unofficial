package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ClientMessageUnloadAttachment implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_unload_attachment");
    public static final CustomPacketPayload.Type<ClientMessageUnloadAttachment> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageUnloadAttachment> CODEC = StreamCodec.ofMember(ClientMessageUnloadAttachment::write, ClientMessageUnloadAttachment::new);

    private final int gunSlotIndex;
    private final AttachmentType attachmentType;

    public ClientMessageUnloadAttachment(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readEnum(AttachmentType.class));
    }

    public ClientMessageUnloadAttachment(int gunSlotIndex, AttachmentType attachmentType) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        Inventory inventory = player.getInventory();
        ItemStack gunItem = inventory.getItem(gunSlotIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun != null) {
            // 服务端校验配件锁
            if (iGun.hasAttachmentLock(gunItem)) {
                return;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, attachmentType);
            if (!attachmentItem.isEmpty()) {
                // 第 15 轮修复：顺序必须是「先卸下、成功后再给物品」。
                //
                // 原先是 inventory.add(...) && unloadAttachment(...)，即<b>先把配件塞进背包</b>，
                // 再去清枪上的 NBT。一旦清 NBT 这步失败（26.2 上 saveItemStack(ItemStack.EMPTY)
                // 因 count 超出 [1,99] 必然抛异常），玩家已经拿到配件、枪上的配件却还在
                // —— 这就是「按卸除无限复制配件」的成因。
                //
                // 现在改为先卸，再校验确实卸掉了，最后才发物品；
                // 若发不进背包（背包满）则回滚，避免配件凭空消失。
                iGun.unloadAttachment(gunItem, attachmentType);
                if (!iGun.getAttachment(gunItem, attachmentType).isEmpty()) {
                    // 卸载没生效，直接放弃，绝不发物品
                    return;
                }
                if (!inventory.add(attachmentItem)) {
                    // 背包放不下 -> 掉在地上，不能让配件蒸发
                    player.drop(attachmentItem, false);
                }
                // 刷新配件数据
                AttachmentPropertyManager.postChangeEvent(player, gunItem);
                // 如果卸载的是扩容弹匣，吐出所有子弹
                if (attachmentType == AttachmentType.EXTENDED_MAG) {
                    iGun.dropAllAmmo(player, gunItem);
                }
                player.inventoryMenu.broadcastChanges();
                NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
            }
        }
    }
}
