package com.tacz.guns.client.event;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public class CommonNetworkCacheEvent {
    public static void onClientPlayerLoggingIn(ClientPacketListener handler, Minecraft client) {
        if (handler.getConnection() == null || handler.getConnection().isMemoryConnection()) {
            return;
        }
        RecipeViewerReloadBridge.clear();
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}