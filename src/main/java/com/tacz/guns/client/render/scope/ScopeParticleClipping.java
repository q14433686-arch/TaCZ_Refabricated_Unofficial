package com.tacz.guns.client.render.scope;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;

/** Local muzzle effects remain viewmodel effects even after detaching into world space.
 * Remote/world effects have no such policy. Sight-only masks must never clip either. */
public final class ScopeParticleClipping {
    private ScopeParticleClipping() {}
    public static boolean ready(boolean hand) {
        if(hand) return ScopeRenderTypes.viewmodelFxClipApplies();
        var mc=Minecraft.getInstance();
        return mc.player!=null && mc.options.getCameraType().isFirstPerson()
                && RenderConfig.SCOPE_MASK_ENABLE.get()
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1)>.35f
                && ScopeDepthCopyState.isMaskCycleValid() && ScopePipRenderState.currentZoom() >= ScopePipRenderState.minMagnification();
    }
}
