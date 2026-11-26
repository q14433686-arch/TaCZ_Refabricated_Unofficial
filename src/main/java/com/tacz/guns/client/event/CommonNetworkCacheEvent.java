package com.tacz.guns.client.event;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.network.message.ServerMessageSyncGunPackPart;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public class CommonNetworkCacheEvent {
    /** Retire any task discarded during voluntary teardown before the next server can send data. */
    public static void onConnectionInit(ClientPacketListener handler, Minecraft client) {
        onLocalLogout(handler.getConnection());
    }

    /** Transport close (possibly off-thread). Never clear the snapshot of a newer server. */
    public static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        // Packet application, viewer state and the chunk receiver are game-thread owned.
        client.execute(() -> {
            var current = client.getConnection();
            if (current == null || current == handler) onLocalLogout(handler.getConnection());
        });
    }

    /** Also called synchronously from both native client-level teardown paths. A voluntary
     * disconnect may discard queued client tasks; it must still retire the departing remote
     * server's indexes. A memory connection shares CommonAssetsManager with its server. */
    public static void onLocalLogout(net.minecraft.network.Connection connection) {
        if (connection == null) return;
        ServerMessageSyncGunPackPart.clearPendingSync();
        RecipeViewerReloadBridge.clear();
        if (connection.isMemoryConnection()) {
            return;
        }
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}