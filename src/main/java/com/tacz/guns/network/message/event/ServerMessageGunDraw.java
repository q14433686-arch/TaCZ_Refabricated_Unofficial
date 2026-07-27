package com.tacz.guns.network.message.event;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.GunDrawEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ServerMessageGunDraw implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_gundraw");
    public static final CustomPacketPayload.Type<ServerMessageGunDraw> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageGunDraw> CODEC = StreamCodec.ofMember(ServerMessageGunDraw::write, ServerMessageGunDraw::new);

    private final int entityId;
    private final ItemStack previousGunItem;
    private final ItemStack currentGunItem;

    public ServerMessageGunDraw(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public ServerMessageGunDraw(int entityId, ItemStack previousGunItem, ItemStack currentGunItem) {
        this.entityId = entityId;
        this.previousGunItem = previousGunItem;
        this.currentGunItem = currentGunItem;
    }

    /**
     * 两个 ItemStack 字段<b>必须</b>用 {@code OPTIONAL_STREAM_CODEC}，不能用 {@code STREAM_CODEC}。
     *
     * <h2>为什么（多人联机致命崩溃的根因）</h2>
     * {@code ItemStack.STREAM_CODEC} 遇到 {@link ItemStack#EMPTY} 会直接抛
     * {@code EncoderException("Empty ItemStack not allowed")}（26.2 字节码
     * {@code ItemStack$2#encode} 第 183 行）。而本消息的
     * {@code previousGunItem} / {@code currentGunItem} <b>天然就可能是空栈</b>：
     *
     * <ul>
     *   <li>{@code LivingEntityDrawGun#draw} 第 49 行明写
     *       {@code data.currentGunItem == null ? ItemStack.EMPTY : ...}
     *       —— 玩家<b>第一次</b>切枪时没有「上一把枪」，必为空栈；</li>
     *   <li>{@code InventoryEvent#onPlayerChangeSelect} 在
     *       {@code oldHotbarSelected == -1} 时直接 {@code draw(ItemStack.EMPTY)}；</li>
     *   <li>玩家<b>丢弃</b>手上物品后该槽位变空，切换/更新时同样传入空栈。</li>
     * </ul>
     *
     * <h2>为什么后果如此严重</h2>
     * 编码异常发生在 {@code Connection#doSendPacket} 的 Netty 线程里，
     * 会直接把该连接<b>踢掉</b>（日志：{@code lost connection: Internal Exception:
     * ... Failed to encode packet ... (tacz:s2c_gundraw)}）。
     * 而本消息是用 {@code NetworkHandler#sendToTrackingEntity} 发给
     * <b>所有能看见该实体的玩家</b>的，于是一次空栈就会把
     * 视野内的每个人（而非动作发起者自己）全部踢下线 ——
     * 实测表现为「服主丢东西，其他人全部断连」「某玩家一进服全服崩」。
     *
     * <h2>与上游对照</h2>
     * 上游 1.21.1 的 {@code ServerMessageGunDraw.STREAM_CODEC} 对这两个字段用的正是
     * {@code ItemStack.OPTIONAL_STREAM_CODEC}（逐字确认）。
     * 本项目移植成手写 {@code write}/read 时误用了非 OPTIONAL 版本，属<b>移植回归</b>。
     *
     * <p>注意同目录其余 5 个事件消息（Fire/FireSelect/Melee/Reload/Shoot）
     * 上游用的确实是非 OPTIONAL 的 {@code STREAM_CODEC}，且它们承载的
     * 必定是一把真实的枪，<b>不应</b>一并改动 —— 已逐个与上游比对确认。
     */
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, previousGunItem);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, currentGunItem);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        doClientEvent(this);
    }

    @Environment(EnvType.CLIENT)
    private static void doClientEvent(ServerMessageGunDraw message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.entityId) instanceof LivingEntity livingEntity) {
            GunDrawEvent gunDrawEvent = new GunDrawEvent(livingEntity, message.previousGunItem, message.currentGunItem, LogicalSide.CLIENT);
            GunDrawEvent.CALLBACK.invoker().post(gunDrawEvent);
        }
    }
}
