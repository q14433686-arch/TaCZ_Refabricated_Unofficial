package com.tacz.guns.client.event;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.network.message.ServerMessageSyncGunPackPart;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

@Environment(EnvType.CLIENT)
public final class CommonNetworkCacheEvent {
    // Client-thread state. A delayed logout must not touch another connection's transfer.
    private static Connection syncConnection;

    private CommonNetworkCacheEvent() {}

    public static void onSync(Connection connection) {
        if (syncConnection != connection) {
            ServerMessageSyncGunPackPart.clearPendingSync();
            syncConnection = connection;
        }
    }

    /** Fallback for transport closure. Ignore an event from an old connection after rejoining. */
    public static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        client.execute(() -> {
            var current = client.getConnection();
            if (current == null || current == handler) onLocalLogout(handler.getConnection());
        });
    }

    /** Both native logout paths invoke this on the client thread before dropping the player. */
    public static void onLocalLogout(Connection connection) {
        if (connection == null || connection != syncConnection) return;
        syncConnection = null;
        ServerMessageSyncGunPackPart.clearPendingSync();
        RecipeViewerReloadBridge.clear();
        // Do not touch the shared provider during integrated-server teardown.
        if (connection.isMemoryConnection()) return;
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}
