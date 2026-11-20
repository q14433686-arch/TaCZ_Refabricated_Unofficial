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
        // 26.2 的 ScopePipRenderer#invalidateLensResources 清的是它那套镜片 GPU 资源
        // （掩码纹理 + 场景目标）。本线的镜内画面目标与深度拷贝由 ScopeDepthCopyState /
        // ScopePipRenderState 按帧自校验（尺寸/格式变了就重建），需要在重载点显式丢弃的只有
        // 排队中的终局覆盖层与投影镜像。
        com.tacz.guns.client.render.scope.ScopeFinalOverlayState.discardPendingOverlays();
        com.tacz.guns.client.render.scope.ScopeProjection.clear();
        GunHurtBobTweak.resetTimestamp();
    }
}
