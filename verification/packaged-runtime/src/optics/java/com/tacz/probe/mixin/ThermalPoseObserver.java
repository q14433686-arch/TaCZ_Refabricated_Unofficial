package com.tacz.probe.mixin;

import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.probe.ThermalPoseFrames;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the actual extracted model state, not just the fixture's requested/network yaw. */
@Mixin(value=ScopeThermalCapture.class, remap=false)
abstract class ThermalPoseObserver {
    @Inject(method="extracted", at=@At("HEAD"), remap=false)
    private static void observe(Entity entity,EntityRenderState state,CallbackInfo ci) {
        if(entity.getUUID().equals(ThermalPoseFrames.watched) && state instanceof LivingEntityRenderState living)
            ThermalPoseFrames.renderedYaw=living.bodyRot;
    }
}
