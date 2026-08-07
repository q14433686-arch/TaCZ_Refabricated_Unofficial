package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.network.NetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Explicit C2S request to clear an already server-recorded feed jam.
 *
 * <p>The request does not remove NBT by itself. The server validates the held
 * gun/profile and starts the existing manual bolt transaction; only its later
 * chambering evidence can clear the fault.</p>
 */
public final class ClientMessageClearFeedJam implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_clear_feed_jam");
    public static final CustomPacketPayload.Type<ClientMessageClearFeedJam> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageClearFeedJam> CODEC = StreamCodec.ofMember(
            ClientMessageClearFeedJam::write, ClientMessageClearFeedJam::new
    );

    public ClientMessageClearFeedJam() {
    }

    public ClientMessageClearFeedJam(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (!IndustryMaintenanceService.requestFeedJamClear(player)) {
            // Return the server's actual held stack even for a stale/invalid
            // request, so an optimistic local bolt cannot become a fake clear.
            NetworkHandler.sendToClientPlayer(new ServerMessageMaintenanceGunState(player.getMainHandItem()), player);
        }
    }
}
