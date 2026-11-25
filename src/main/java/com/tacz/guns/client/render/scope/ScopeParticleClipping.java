package com.tacz.guns.client.render.scope;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;

/** A local viewmodel effect can detach into world space before the next hand is drawn. */
public final class ScopeParticleClipping {
    private ScopeParticleClipping() {}

    public static boolean ready(boolean hand) {
        if (hand) return ScopeRenderTypes.viewmodelFxClipApplies();
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && RenderConfig.SCOPE_MASK_ENABLE.get() && !IrisCompat.isRenderShadow()
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .35f
                && ScopeDepthCopyState.hasParticleAperture();
    }
}
