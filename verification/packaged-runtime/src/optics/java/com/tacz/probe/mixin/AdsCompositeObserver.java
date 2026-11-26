package com.tacz.probe.mixin;

import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.probe.AdsTransitionSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ScopePipRenderState.class,remap=false)
abstract class AdsCompositeObserver {
    @Inject(method="compositeAfterHand",at=@At("HEAD"),remap=false)
    private static void beforeNative(Minecraft mc,CallbackInfo ci) { AdsTransitionSmoke.beforeLens(mc,false); }
    @Inject(method="compositeAfterIrisFinal",at=@At("HEAD"),remap=false)
    private static void beforeIris(Minecraft mc,CallbackInfo ci) { AdsTransitionSmoke.beforeLens(mc,true); }
}
