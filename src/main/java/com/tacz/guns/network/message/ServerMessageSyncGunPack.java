package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class ServerMessageSyncGunPack implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_gunpack");
    public static final CustomPacketPayload.Type<ServerMessageSyncGunPack> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncGunPack> CODEC = StreamCodec.ofMember(ServerMessageSyncGunPack::write, ServerMessageSyncGunPack::new);

    private final Map<DataType, Map<Identifier, String>> cache;

    public ServerMessageSyncGunPack(FriendlyByteBuf buf) {
        this(readCache(buf, Short.MAX_VALUE));
    }

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

    // Shared inner wire format. The legacy channel keeps its old string limit.
    static Map<DataType, Map<Identifier, String>> readCache(FriendlyByteBuf buf, int maxChars) {
        int types = buf.readVarInt();
        if (types < 0 || types > DataType.values().length) throw new DecoderException("Invalid data type count: " + types);
        Map<DataType, Map<Identifier, String>> cache = new EnumMap<>(DataType.class);
        for (int i = 0; i < types; i++) {
            DataType type = buf.readEnum(DataType.class);
            int count = buf.readVarInt();
            if (count < 0 || count > buf.readableBytes() / 2) throw new DecoderException("Invalid entry count: " + count);
            var entries = new HashMap<Identifier, String>();
            for (int j = 0; j < count; j++) {
                Identifier id = buf.readIdentifier();
                if (entries.put(id, buf.readUtf(maxChars)) != null) throw new DecoderException("Duplicate resource: " + id);
            }
            if (cache.put(type, entries) != null) throw new DecoderException("Duplicate data type: " + type);
        }
        if (buf.isReadable()) throw new DecoderException("Trailing gun pack data");
        return cache;
    }

    public void write(FriendlyByteBuf buf) {
        writeCache(buf, cache, Short.MAX_VALUE);
    }

    static void writeCache(FriendlyByteBuf buf, Map<DataType, Map<Identifier, String>> cache, int maxChars) {
        buf.writeVarInt(cache.size());
        for (Map.Entry<DataType, Map<Identifier, String>> typeEntry : cache.entrySet()) {
            buf.writeEnum(typeEntry.getKey());
            Map<Identifier, String> entries = typeEntry.getValue();
            buf.writeVarInt(entries.size());
            for (Map.Entry<Identifier, String> entry : entries.entrySet()) {
                buf.writeIdentifier(entry.getKey());
                buf.writeUtf(entry.getValue(), maxChars);
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        Connection connection = player.connection.getConnection();
        Minecraft.getInstance().execute(() -> {
            if (isCurrent(connection)) installCache(cache, connection);
        });
    }

    @Environment(EnvType.CLIENT)
    static boolean isCurrent(Connection connection) {
        var handler = Minecraft.getInstance().getConnection();
        return handler != null && handler.getConnection() == connection && connection.isConnected();
    }

    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    @Environment(EnvType.CLIENT)
    static void installCache(Map<DataType, Map<Identifier, String>> cache, Connection connection) {
        if (!connection.isMemoryConnection()) {
            CommonAssetsManager.clearInstance();
        }
        // Ordering is intentional: viewers must observe the newly installed cache and rebuilt index.
        CommonNetworkCache.INSTANCE.fromNetwork(cache);
        ClientIndexManager.reload();
        RecipeViewerReloadBridge.requestReload();
    }
}
