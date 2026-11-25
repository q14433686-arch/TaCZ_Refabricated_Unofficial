package com.tacz.guns.test.mixin;

import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.test.ReimaginedSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only absent-caster control. Never included in the game jar. */
@Mixin(value=BedrockRenderSnapshot.class,remap=false)
abstract class ReimaginedCasterControl {
    @Inject(method="submitGeometry",at=@At("HEAD"),cancellable=true)
    private void omit(CallbackInfo ci) { if (ReimaginedSmoke.omitCaster && IrisCompat.isRenderShadow()) ci.cancel(); }
}
