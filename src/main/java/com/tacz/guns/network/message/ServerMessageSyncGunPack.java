package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.compat.CreativeTabRefresh;
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
        // 诊断：连专服时，客户端收到的同步包各类目数量。
        // 若这里全是 0，问题在服务端没发数据；若数量正常但仍紫黑，问题在客户端
        // CommonNetworkCache 解析或 ClientIndexManager 重建（见其 warn 日志）。
        var c = message.cache;
        GunMod.LOGGER.info("[GunPackSync] Client received (remote={}): GUN_INDEX={}, AMMO_INDEX={}, ATTACHMENT_INDEX={}, BLOCK_INDEX={}, GUN_DATA={}, ATTACHMENT_DATA={}, BLOCK_DATA={}, RECIPES={}",
                remoteConnection,
                c.getOrDefault(DataType.GUN_INDEX, Map.of()).size(),
                c.getOrDefault(DataType.AMMO_INDEX, Map.of()).size(),
                c.getOrDefault(DataType.ATTACHMENT_INDEX, Map.of()).size(),
                c.getOrDefault(DataType.BLOCK_INDEX, Map.of()).size(),
                c.getOrDefault(DataType.GUN_DATA, Map.of()).size(),
                c.getOrDefault(DataType.ATTACHMENT_DATA, Map.of()).size(),
                c.getOrDefault(DataType.BLOCK_DATA, Map.of()).size(),
                c.getOrDefault(DataType.RECIPES, Map.of()).size());
        // Ordering is intentional: viewers must observe the newly installed cache and rebuilt index.
        CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
        // 通知客户端重新构建ClientIndex
        ClientIndexManager.reload();
        // 专服关键：vanilla 在同步包到达之前就构建了一次创造标签，那时 common 索引还是空的，
        // 导致 TaCZ 创造标签里没有任何枪/弹/配件/工作台（从标签或派生路径拿物品就得到裸物品 -> 紫黑）。
        // 现在索引已就绪，强制重建创造标签。详见 CreativeTabRefresh 的注释。
        CreativeTabRefresh.rebuildAfterGunPackSync();
        RecipeViewerReloadBridge.requestReload();
    }
}
