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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class ServerMessageSyncGunPack implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_gunpack");
    public static final CustomPacketPayload.Type<ServerMessageSyncGunPack> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncGunPack> CODEC = StreamCodec.ofMember(ServerMessageSyncGunPack::write, ServerMessageSyncGunPack::new);

    private final Map<DataType, Map<Identifier, String>> cache;

    public ServerMessageSyncGunPack(FriendlyByteBuf buf) {
        this(readCache(buf));
    }

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

    /**
     * 26.3: {@code FriendlyByteBuf#readMap/writeMap} 已被移除，改为手写「先长度后条目」的循环。
     * 线格式与 26.2 的 readMap/writeMap 完全一致（varint 长度 + 逐条 key/value），
     * 所以不影响与旧客户端以外的任何东西 —— 本包本来就要求两端同版本。
     */
    private static Map<DataType, Map<Identifier, String>> readCache(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        Map<DataType, Map<Identifier, String>> cache = new HashMap<>(Math.max(16, typeCount));
        for (int i = 0; i < typeCount; i++) {
            DataType dataType = buf.readEnum(DataType.class);
            int entryCount = buf.readVarInt();
            Map<Identifier, String> entries = new HashMap<>(Math.max(16, entryCount));
            for (int j = 0; j < entryCount; j++) {
                Identifier id = buf.readIdentifier();
                entries.put(id, buf.readUtf());
            }
            cache.put(dataType, entries);
        }
        return cache;
    }

    public void write(FriendlyByteBuf buf) {
        Map<DataType, Map<Identifier, String>> cache = getCache();
        buf.writeVarInt(cache.size());
        for (Map.Entry<DataType, Map<Identifier, String>> typeEntry : cache.entrySet()) {
            buf.writeEnum(typeEntry.getKey());
            Map<Identifier, String> entries = typeEntry.getValue();
            buf.writeVarInt(entries.size());
            for (Map.Entry<Identifier, String> entry : entries.entrySet()) {
                buf.writeIdentifier(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        // Network delivery need not be on the client event loop. Cache installation, index rebuilding,
        // and optional recipe-viewer registration all touch client-owned state, so keep their order
        // together on Minecraft's executor.
        Minecraft.getInstance().execute(() -> {
            boolean remoteConnection = player.connection.getConnection() != null
                    && !player.connection.getConnection().isMemoryConnection();
            doSync(this, remoteConnection);
        });
    }


    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    @Environment(EnvType.CLIENT)
    private static void doSync(ServerMessageSyncGunPack message, boolean remoteConnection) {
        if (remoteConnection) {
            CommonAssetsManager.clearInstance();
        }
        // Ordering is intentional: viewers must observe the newly installed cache and rebuilt index.
        CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
        // 通知客户端重新构建ClientIndex
        ClientIndexManager.reload();
        RecipeViewerReloadBridge.requestReload();
    }
}
