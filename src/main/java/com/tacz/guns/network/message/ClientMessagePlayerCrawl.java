package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.config.sync.SyncConfig;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessagePlayerCrawl implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_crawl");
    public static final CustomPacketPayload.Type<ClientMessagePlayerCrawl> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerCrawl> CODEC = StreamCodec.ofMember(ClientMessagePlayerCrawl::write, ClientMessagePlayerCrawl::new);

    private final boolean isCrawl;

    public ClientMessagePlayerCrawl(boolean isCrawl) {
        this.isCrawl = isCrawl;
    }

    public ClientMessagePlayerCrawl(FriendlyByteBuf buf) {
        this.isCrawl = buf.readBoolean();
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isCrawl);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (!SyncConfig.ENABLE_CRAWL.get()) {
            return;
        }
        IGunOperator.fromLivingEntity(player).crawl(isCrawl);
    }
}
