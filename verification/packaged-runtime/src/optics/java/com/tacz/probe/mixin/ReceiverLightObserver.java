package com.tacz.probe.mixin;

import com.tacz.guns.client.render.light.WorldFlashFrame;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import com.tacz.probe.LightFrames;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=WorldFlashFrame.class,remap=false)
abstract class ReceiverLightObserver {
    @Inject(method="submitWorld",at=@At("HEAD"),remap=false)
    private static void begin(LevelRenderState state,boolean entities,Runnable original,CallbackInfo ci) { LightFrames.beginScope(state,entities); }
    // Only the two original.run sites (fast/no-light and scoped). Do not observe the
    // finally restore Runnable list, which intentionally contains ambient UV2 again.
    @Inject(method="submitWorld",at={@At(value="INVOKE",target="Ljava/lang/Runnable;run()V",ordinal=0),
            @At(value="INVOKE",target="Ljava/lang/Runnable;run()V",ordinal=1)},remap=false)
    private static void submitted(LevelRenderState state,boolean entities,Runnable original,CallbackInfo ci) { LightFrames.submitted(state,entities); }
    @Inject(method="submitWorld",at=@At("RETURN"),remap=false)
    private static void end(LevelRenderState state,boolean entities,Runnable original,CallbackInfo ci) { LightFrames.endScope(); }
}

@Mixin(value=ScopeThermalCapture.class,remap=false)
abstract class ReceiverViewObserver {
    @Inject(method="worldView",at=@At("HEAD"),remap=false)
    private static void view(Matrix4f matrix,CallbackInfo ci) { LightFrames.worldView(matrix); }
}

@Mixin(value=DisplayRackRenderer.class,remap=false)
abstract class ReceiverSlotObserver {
    @Inject(method="slotLight",at=@At("RETURN"),remap=false)
    private static void slot(DisplayRackRenderer.State state,WorldFlashFrame flash,Direction face,CallbackInfoReturnable<Integer> cir) {
        LightFrames.rack(state.blockPos,cir.getReturnValue());
    }
}
