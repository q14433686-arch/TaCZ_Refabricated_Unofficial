package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.IndustrialSalvageMenu;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Client request only; recovery eligibility and all extraction stay server-side. */
public record ClientMessageSalvageIndustry(int menuId) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_salvage_industry");
    public static final CustomPacketPayload.Type<ClientMessageSalvageIndustry> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageSalvageIndustry> CODEC = StreamCodec.ofMember(
            ClientMessageSalvageIndustry::write, ClientMessageSalvageIndustry::new
    );

    public ClientMessageSalvageIndustry(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.containerMenu.containerId == menuId && player.containerMenu instanceof IndustrialSalvageMenu menu) {
            menu.salvage(player);
        }
    }
}
