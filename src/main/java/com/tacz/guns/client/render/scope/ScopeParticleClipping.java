package com.tacz.guns.client.render.scope;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;

/** Local muzzle effects remain viewmodel effects even after detaching into world space.
 * Remote/world effects have no such policy. Sight-only masks must never clip either. */
public final class ScopeParticleClipping {
    private ScopeParticleClipping() {}

    /**
     * 1.21.11 版：26.2 的判据读的是它那套掩码纹理与几何（{@code ScopeMaskRenderer} /
     * {@code ScopeMaskTextureHandle}）。本线的"孔径"是 ScopeDepthCopyState 记录的深度拷贝周期，
     * 因此判据换成"同一帧内的孔径周期是否有效"，语义仍是"镜内像素由谁负责"。
     * （正文取自 PR#126 的 1.21.11 编译验证版本。）
     */
    public static boolean ready(boolean hand) {
        if (hand) return ScopeRenderTypes.shouldClipViewmodel();
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType().isFirstPerson()
                && RenderConfig.SCOPE_MASK_ENABLE.get() && !IrisCompat.shouldDisableScopeMaskUnderShaderPack()
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .35f
                && ScopeDepthCopyState.hasMaskCycleThisFrame()
                && ScopePipRenderState.shouldClipViewmodelForeground();
    }
}
