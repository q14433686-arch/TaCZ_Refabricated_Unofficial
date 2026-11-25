package com.tacz.guns.test.mixin;

import com.tacz.guns.client.model.IFunctionalSubmitter.ExtractionContext;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.test.MuzzleQuadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MuzzleFlashRender.class, remap=false)
abstract class LegacyFlashObserver {
    @Inject(method="extract", at=@At(value="INVOKE",target="Lcom/tacz/guns/client/model/IFunctionalSubmitter$ExtractionContext;add(Lcom/tacz/guns/client/model/IFunctionalSubmitter$SubmitTask;)V"))
    private void onLegacySubmit(ExtractionContext context, CallbackInfo ci) { MuzzleQuadProbe.legacy(); }
}
