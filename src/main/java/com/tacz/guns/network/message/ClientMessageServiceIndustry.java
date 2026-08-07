package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.IndustrialServiceBenchMenu;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** C2S request for one validated service-bench transaction. */
public record ClientMessageServiceIndustry(int menuId, int action) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_service_industry");
    public static final CustomPacketPayload.Type<ClientMessageServiceIndustry> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageServiceIndustry> CODEC = StreamCodec.ofMember(
            ClientMessageServiceIndustry::write, ClientMessageServiceIndustry::new
    );

    public ClientMessageServiceIndustry(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
        buffer.writeVarInt(action);
    }

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.containerMenu.containerId == menuId && player.containerMenu instanceof IndustrialServiceBenchMenu menu) {
            menu.service(player, action);
        }
    }
}
