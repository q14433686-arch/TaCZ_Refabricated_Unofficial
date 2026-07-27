package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessageCraft implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_craft");
    public static final CustomPacketPayload.Type<ClientMessageCraft> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageCraft> CODEC = StreamCodec.ofMember(ClientMessageCraft::write, ClientMessageCraft::new);

    private final Identifier recipeId;
    private final int menuId;

    public ClientMessageCraft(FriendlyByteBuf buf) {
        this(buf.readIdentifier(), buf.readVarInt());
    }

    public ClientMessageCraft(Identifier recipeId, int menuId) {
        this.recipeId = recipeId;
        this.menuId = menuId;
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(recipeId);
        buf.writeVarInt(menuId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.containerMenu.containerId == menuId && player.containerMenu instanceof GunSmithTableMenu menu) {
            menu.doCraft(recipeId, player);
        }
    }
}
