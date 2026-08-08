package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.AmmunitionHandlingBenchMenu;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** C2S request only; every timed per-round mutation stays in the bench entity. */
public record ClientMessageHandleAmmunition(int menuId, int action, int inputIndex) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_handle_ammunition");
    public static final CustomPacketPayload.Type<ClientMessageHandleAmmunition> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageHandleAmmunition> CODEC = StreamCodec.ofMember(
            ClientMessageHandleAmmunition::write, ClientMessageHandleAmmunition::new
    );

    public ClientMessageHandleAmmunition(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
        buffer.writeVarInt(action);
        buffer.writeVarInt(inputIndex);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.containerMenu.containerId == menuId && player.containerMenu instanceof AmmunitionHandlingBenchMenu menu
                && menu.stillValid(player)) {
            menu.start(player, action, inputIndex);
        }
    }
}
