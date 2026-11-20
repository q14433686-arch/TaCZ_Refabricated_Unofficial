package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class ThermalEntityMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void tacz$readHeatTags(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        ScopeThermalCapture.extracted(entity, cir.getReturnValue());
        com.tacz.guns.client.particle.NativeMuzzleEffects.extracted(entity, cir.getReturnValue());
    }

    @WrapMethod(method = "submit")
    private void tacz$scopeThermalBody(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                       PoseStack pose, SubmitNodeCollector out, Operation<Void> original) {
        int muzzleOwner = com.tacz.guns.client.particle.NativeMuzzleEffects.beginEntity(state);
        boolean previous = ScopeThermalCapture.beginEntity(state);
        try { original.call(state, camera, x, y, z, pose, out); }
        finally { ScopeThermalCapture.endEntity(previous); com.tacz.guns.client.particle.NativeMuzzleEffects.endEntity(muzzleOwner); }
    }
}
