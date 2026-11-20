package com.tacz.guns.mixin.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 equivalent of 26.2's LevelExtractorScopePassMixin (allChanged lives on LevelRenderer in 26.1.2).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererAllChangedScopePassMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
    private void tacz$noFullReloadDuringScopePass(CallbackInfo ci) {
        if (!ScopePipRenderer.isScopePassActive()
                && !IrisScopePipelineCompat.isBuildingScopePipeline()) {
            IrisScopePipelineCompat.onLevelRendererReload();
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Suppressed a full renderer reload requested during the "
                    + "scope pass (or the scope-pipeline prewarm build). Iris asks for it once when a "
                    + "pipeline first renders, and it would make Voxy rebind itself to the scope "
                    + "pipeline for the rest of the session, permanently corrupting distant terrain "
                    + "in the main view (or crashing after the idle release destroys that pipeline). "
                    + "The block-id state it refreshes is global and already set by the main pipeline.");
        }
        ci.cancel();
    }
}
