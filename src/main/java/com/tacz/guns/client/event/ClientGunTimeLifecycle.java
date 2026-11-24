package com.tacz.guns.client.event;

import cn.sh1rocu.tacz.api.event.ClientPlayerNetworkEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.util.time.GunTime;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.Minecraft;

/** Only the client entry point drives pause. Disconnect invalidates deferred work even on a memory connection. */
public final class ClientGunTimeLifecycle {
    private ClientGunTimeLifecycle() {}
    public static void tick(Minecraft client) { GunTime.setPaused(client.level != null && client.isPaused()); }
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        var player = event.getPlayer();
        if (player == null) return; // No play session (e.g. closing from the title screen).
        IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder().cancelPendingActions();
        com.tacz.guns.client.model.papi.RangefinderReadout.clear();
        GunTime.resetEpoch();
        LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
        RefitTransform.resetTimestamps();
        RenderCrosshairEvent.resetTimestamps();
        KillAmountOverlay.resetTimestamp();
        ClientHitMark.resetTimestamp();
        FirstPersonRenderGunEvent.resetTimestamps();
        CameraSetupEvent.resetTimestamp();
        MuzzleFlashRender.resetTimestamps();
        com.tacz.guns.client.particle.NativeMuzzleEffects.clear();
        com.tacz.guns.client.render.scope.ScopeLensFrame.clear();
        GunHurtBobTweak.resetTimestamp();
    }
}
