package com.tacz.guns.test.mixin;

import com.tacz.guns.client.gameplay.LocalPlayerAim;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.test.AdsTransitionSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real aim key, tick interpolation and hand pose. Only fix the test's ADS duration in ticks:
 * the wall-clock delta on a software renderer (especially on the first narrow pass) can span an entire ADS. */
@Mixin(LocalPlayerAim.class)
abstract class AdsStepControl {
    @Inject(method="getAlphaProgress", at=@At("HEAD"), cancellable=true)
    private void slowAim(GunData gun, CallbackInfoReturnable<Float> cir) {
        if (AdsTransitionSmoke.running) cir.setReturnValue(.05f);
    }
}

@Mixin(ScopePipRenderState.class)
abstract class AdsCompositeObserver {
    @Inject(method="compositeAfterHand", at=@At("HEAD"))
    private static void beforeNative(Minecraft mc, CallbackInfo ci) { AdsTransitionSmoke.beforeLens(mc, false); }
    @Inject(method="compositeAfterIrisFinal", at=@At("HEAD"))
    private static void beforeIris(Minecraft mc, CallbackInfo ci) { AdsTransitionSmoke.beforeLens(mc, true); }
}
