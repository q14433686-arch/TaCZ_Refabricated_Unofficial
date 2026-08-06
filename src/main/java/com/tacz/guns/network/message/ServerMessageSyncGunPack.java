package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.compat.RecipeViewerSyncBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

public class ServerMessageSyncGunPack implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_gunpack");
    public static final CustomPacketPayload.Type<ServerMessageSyncGunPack> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncGunPack> CODEC = StreamCodec.ofMember(ServerMessageSyncGunPack::write, ServerMessageSyncGunPack::new);

    private final Map<DataType, Map<Identifier, String>> cache;

    public ServerMessageSyncGunPack(FriendlyByteBuf buf) {
        this(buf.readMap(buf1 -> buf1.readEnum(DataType.class),
                buf2 -> buf2.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readUtf)));
    }

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeMap(getCache(), FriendlyByteBuf::writeEnum, (buf1, map) ->
                buf1.writeMap(map, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeUtf));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        boolean remoteConnection = player.connection.getConnection() != null && !player.connection.getConnection().isMemoryConnection();
        doSync(this, remoteConnection);
    }


    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    @Environment(EnvType.CLIENT)
    private static void doSync(ServerMessageSyncGunPack message, boolean remoteConnection) {
        if (remoteConnection) {
            CommonAssetsManager.clearInstance();
        }
        CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
        // 通知客户端重新构建ClientIndex
        ClientIndexManager.reload();
        // The cache arrives after JEI/REI plugin bootstrap on remote clients.
        // REI reads it through a live generator; JEI receives a queued runtime
        // addition through this optional bridge after all client indexes exist.
        RecipeViewerSyncBridge.onCommonDataSynchronized();
    }
}
