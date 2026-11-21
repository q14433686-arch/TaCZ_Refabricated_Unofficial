package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.newly.IrisCompatNewly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris 26.1 invokes {@code renderShadows} before {@code LevelRenderer#cullTerrain}, causing
 * {@code SodiumWorldRenderer#processChunkEvents()} to drain newly loaded chunk events while
 * shadow render-list scope is active. When {@code iris$endShadowRenderListScope()} restores the
 * saved {@code regularNeedsGraphUpdate = false}, regular terrain culling skips graph rebuilds
 * and newly loaded chunks stay unrendered until the camera moves.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class IrisSodiumWorldRendererMixin {
    @Inject(method = "processChunkEvents", at = @At("HEAD"), cancellable = true, require = 0)
    private void tacz$deferChunkEventsOutOfShadowPass(CallbackInfo ci) {
        if (IrisCompatNewly.isRenderShadow()) {
            ci.cancel();
        }
    }
}
