package com.tacz.probe.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.client.render.scope.ScopeLensFrame;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.guns.client.render.scope.UserEnvReadback;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Diagnostic-only lens-gate trace; inert unless UserEnvReadback.startTrace() is active. */
@Mixin(value = ScopeThermalCapture.class, remap = false)
abstract class LensTraceHeat {
    @Inject(method = "finish", at = @At("HEAD"), remap = false)
    private static void tacz$before(RenderTarget scene, Matrix4f view, CallbackInfo ci) { UserEnvReadback.trace("heat>"); }

    @Inject(method = "finish", at = @At("RETURN"), remap = false)
    private static void tacz$after(RenderTarget scene, Matrix4f view, CallbackInfo ci) { UserEnvReadback.traceHeat(); }
}
