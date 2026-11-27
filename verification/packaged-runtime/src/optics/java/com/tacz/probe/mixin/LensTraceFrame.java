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
@Mixin(value = ScopeLensFrame.class, remap = false)
abstract class LensTraceFrame {
    @Inject(method = "beginFrame", at = @At("HEAD"), remap = false)
    private static void tacz$frameHead(Minecraft mc, CallbackInfo ci) { UserEnvReadback.traceFrameHead(mc); }

    @Inject(method = "beginFrame", at = @At("TAIL"), remap = false)
    private static void tacz$frame(Minecraft mc, CallbackInfo ci) { UserEnvReadback.traceFrame(); }

    @Inject(method = "observe", at = @At("HEAD"), remap = false)
    private static void tacz$observe(ScopeLensConfig lens, CallbackInfo ci) { UserEnvReadback.trace("obs=" + UserEnvReadback.mode(lens)); }

    @Inject(method = "ocular", at = @At("RETURN"), remap = false)
    private static void tacz$ocular(ScopeLensConfig lens, List<?> geometry, CallbackInfo ci) { UserEnvReadback.traceOcular(lens); }
}
