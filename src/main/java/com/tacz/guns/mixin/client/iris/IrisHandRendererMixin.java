package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import net.irisshaders.iris.pathways.HandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris 1.11+ on 26.2 renders the first-person hand through its own {@link HandRenderer}.
 *
 * <p>That path intentionally bypasses {@code GameRenderer#renderItemInHand}; therefore TACZ's old
 * HEAD/RETURN hook never sets {@code inHandPass} while a shader pack is active. The ocular mask was
 * consequently not refreshed, but the scope body/reticle still used the clipped render pipeline and
 * sampled the stale/empty mask. On Iris + NVIDIA this showed up as random holes/cuts in the gun and
 * attachments while aiming down sights.</p>
 *
 * <p>Mirror the vanilla hand-pass bracket around Iris' own dispatcher. The mask renderer itself only
 * performs GPU work when ocular geometry was submitted during this hand pass, so this is cheap.</p>
 */
@Mixin(value = HandRenderer.class, remap = false)
public abstract class IrisHandRendererMixin {

    @Inject(method = "renderSolid", at = @At("HEAD"))
    private void tacz$beginSolidHandPass(CallbackInfo ci) {
        ScopeMaskRenderer.setInHandPass(true);
    }

    @Inject(method = "renderSolid", at = @At("RETURN"))
    private void tacz$endSolidHandPass(CallbackInfo ci) {
        ScopeMaskRenderer.setInHandPass(false);
    }

    @Inject(method = "renderTranslucent", at = @At("HEAD"))
    private void tacz$beginTranslucentHandPass(CallbackInfo ci) {
        ScopeMaskRenderer.setInHandPass(true);
    }

    @Inject(method = "renderTranslucent", at = @At("RETURN"))
    private void tacz$endTranslucentHandPass(CallbackInfo ci) {
        ScopeMaskRenderer.setInHandPass(false);
    }
}
