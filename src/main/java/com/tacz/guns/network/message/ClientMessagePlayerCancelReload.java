package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessagePlayerCancelReload implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_cancel_reload");
    public static final CustomPacketPayload.Type<ClientMessagePlayerCancelReload> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerCancelReload> CODEC = StreamCodec.ofMember(ClientMessagePlayerCancelReload::write, ClientMessagePlayerCancelReload::new);

    public ClientMessagePlayerCancelReload() {

    }

    public ClientMessagePlayerCancelReload(FriendlyByteBuf buf) {
        this();
    }

        public void write(FriendlyByteBuf buf) {

    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        IGunOperator.fromLivingEntity(player).cancelReload();
    }
}
