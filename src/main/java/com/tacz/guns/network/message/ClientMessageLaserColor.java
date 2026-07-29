package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ClientMessageLaserColor implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_laser_color");
    public static final CustomPacketPayload.Type<ClientMessageLaserColor> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageLaserColor> CODEC = StreamCodec.ofMember(ClientMessageLaserColor::write, ClientMessageLaserColor::new);

    private final Map<AttachmentType, Integer> colorMap = new HashMap<>();
    private boolean applyGunColor = false;
    private int gunColor = 0;

    private int gunSlotIndex = -1;

    private ClientMessageLaserColor() {

    }

    public ClientMessageLaserColor(FriendlyByteBuf buf) {
        this.colorMap.putAll(buf.readMap(buf1 -> buf1.readEnum(AttachmentType.class), FriendlyByteBuf::readInt));
        this.applyGunColor = buf.readBoolean();
        this.gunColor = buf.readInt();
        this.gunSlotIndex = buf.readInt();
    }

    public ClientMessageLaserColor(@NotNull ItemStack gun, int gunSlotIndex) {
        if (gun.getItem() instanceof IGun iGun) {
            for (AttachmentType type : AttachmentType.values()) {
                ItemStack attachment = iGun.getAttachment(gun, type);
                if (attachment.getItem() instanceof IAttachment iAttachment) {
                    if (iAttachment.hasCustomLaserColor(attachment)) {
                        colorMap.put(type, iAttachment.getLaserColor(attachment));
                    }
                }
            }
            if (iGun.hasCustomLaserColor(gun)) {
                this.gunColor = iGun.getLaserColor(gun);
                this.applyGunColor = true;
            }
            this.gunSlotIndex = gunSlotIndex;
        }
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeMap(colorMap, FriendlyByteBuf::writeEnum, FriendlyByteBuf::writeInt);
        buf.writeBoolean(applyGunColor);
        buf.writeInt(gunColor);
        buf.writeInt(gunSlotIndex);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (this.gunSlotIndex == -1) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack gunItem = inventory.getItem(gunSlotIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun != null) {
            for (var entry : colorMap.entrySet()) {
                AttachmentType type = entry.getKey();
                int color = entry.getValue();
                // 【必须改「枪上那份配件 NBT」，不能改 getAttachment() 返回的 ItemStack】
                //
                // getAttachment(gun, type) 内部是
                //     ItemNbtUtils.loadItemStack(nbt.getCompoundOrEmpty(key))
                // —— 每次调用都用 Codec 从 NBT【反序列化出一个全新的 ItemStack】，
                // 它与枪上存的那份数据没有任何引用关系。
                //
                // 原先这里写的是
                //     ItemStack attachment = iGun.getAttachment(gunItem, type);
                //     iAttachment.setLaserColor(attachment, color);
                // 等于把颜色写进了一个【临时副本】，方法返回后该副本即被丢弃，
                // 枪上的配件 NBT 一个字节都没变。于是服务端「保存成功」、
                // 客户端界面上看着也变了（因为改装界面用的是本地预览的那份），
                // 一旦退出界面重新从物品 NBT 读取，就立刻回到默认色 ——
                // 正是用户实测到的「改完镭射颜色，一退出界面就变回去」。
                //
                // 上游的写法是就地改 tag 再写回（逐行对照 1.21.1 的 handle）：
                //     CompoundTag tag = iGun.getAttachmentTag(gunItem, type);
                //     if (tag != null) { AttachmentItemDataAccessor.setLaserColorToTag(tag, color); }
                //     iGun.setAttachmentTag(gunItem, type, tag);
                // getAttachmentTag/setAttachmentTag 操作的是枪 NBT 里
                // 「配件 ItemStack 的 components.custom_data」那一层，改动会真正落盘。
                CompoundTag tag = iGun.getAttachmentTag(gunItem, type);
                if (tag != null) {
                    AttachmentItemDataAccessor.setLaserColorToTag(tag, color);
                    iGun.setAttachmentTag(gunItem, type, tag);
                }
            }
            if (applyGunColor) {
                // 枪自身的镭射色（内置镭射）走的是枪本体的 custom_data，
                // setLaserColor 内部就是 ItemNbtUtils.updateTag(gun, ...)，
                // 直接作用在 gunItem 上，没有副本问题。
                iGun.setLaserColor(gunItem, gunColor);
            }
        }
    }
}
