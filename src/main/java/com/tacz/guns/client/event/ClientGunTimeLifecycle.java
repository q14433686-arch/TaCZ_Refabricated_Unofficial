package com.tacz.guns.client.event;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.util.time.GunTime;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Only the client entry point drives pause. Disconnect invalidates deferred work even on a memory connection. */
public final class ClientGunTimeLifecycle {
    private ClientGunTimeLifecycle() {}
    public static void tick(Minecraft client) { GunTime.setPaused(client.level != null && client.isPaused()); }
    public static void disconnect(ClientPacketListener connection, Minecraft client) {
        if (client.player != null) IClientPlayerGunOperator.fromLocalPlayer(client.player).getDataHolder().cancelPendingActions();
        GunTime.resetEpoch();
        LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
        RefitTransform.resetTimestamps();
        RenderCrosshairEvent.resetTimestamps();
        KillAmountOverlay.resetTimestamp();
        ClientHitMark.resetTimestamp();
        FirstPersonRenderGunEvent.resetTimestamps();
        CameraSetupEvent.resetTimestamp();
        MuzzleFlashRender.resetTimestamps();
        GunHurtBobTweak.resetTimestamp();
    }
}
