package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries entity ownership through native muzzle FX and posed thermal model submission. */
@Mixin(EntityRenderDispatcher.class)
abstract class ThermalEntityMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void tacz$readMuzzleOwner(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        NativeMuzzleEffects.extracted(entity, cir.getReturnValue());
        com.tacz.guns.client.render.scope.ScopeThermalCapture.extracted(entity, cir.getReturnValue());
    }

    @WrapMethod(method = "submit")
    private void tacz$muzzleOwnerScope(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                       PoseStack pose, SubmitNodeCollector out, Operation<Void> original) {
        int muzzleOwner = NativeMuzzleEffects.beginEntity(state);
        boolean heat = com.tacz.guns.client.render.scope.ScopeThermalCapture.beginEntity(state);
        try {
            original.call(state, camera, x, y, z, pose, out);
        } finally {
            com.tacz.guns.client.render.scope.ScopeThermalCapture.endEntity(heat);
            NativeMuzzleEffects.endEntity(muzzleOwner);
        }
    }
}
