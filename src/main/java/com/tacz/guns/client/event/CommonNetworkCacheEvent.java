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
import net.minecraft.network.Connection;

@Environment(EnvType.CLIENT)
public final class CommonNetworkCacheEvent {
    private CommonNetworkCacheEvent() {}

    /** Fallback for transport closure. Ignore an event from an old connection after rejoining. */
    public static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        client.execute(() -> {
            var current = client.getConnection();
            if (current == null || current == handler) onLocalLogout(handler.getConnection());
        });
    }

    /** Both native logout paths invoke this on the client thread before dropping the player. */
    public static void onLocalLogout(Connection connection) {
        if (connection == null) return;
        ServerMessageSyncGunPackPart.clearPendingSync();
        RecipeViewerReloadBridge.clear();
        // The integrated server owns this provider and can still be saving its world.
        if (connection.isMemoryConnection()) return;
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}
