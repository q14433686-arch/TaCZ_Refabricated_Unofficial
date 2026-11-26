package com.tacz.guns.client.render.scope;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import java.util.concurrent.CompletableFuture;

/** Test-mod-only access to the real production attachment; no production debug API. */
public final class ThermalReadback {
    public record Pixels(int width, int height, int hot, int centralHot, String capture) {}
    public static Pixels read(ClientGameTestContext ctx) {
        var future = new CompletableFuture<Pixels>();
        ctx.runOnClient(mc -> {
            var target = ScopeThermalCapture.current();
            if (target == null) throw new AssertionError("Thermal attachment not ready: expected=" + ScopeLensFrame.expected
                    + " current=" + ScopeLensFrame.current() + " bounds=" + java.util.Arrays.toString(ScopeLensFrame.currentBounds())
                    + " projection=" + ScopeProjection.current() + " aim=" + com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0)
                    + " state=" + captureState());
            Screenshot.takeScreenshot(target, pixels -> {
                try (pixels) {
                    int w = pixels.getWidth(), h = pixels.getHeight(), hot = 0, central = 0;
                    for (int y=0; y<h; y++) for (int x=0; x<w; x++) if ((pixels.getPixel(x,y)&255)>127) {
                        hot++;
                        if (x>w/3 && x<2*w/3 && y>h/3 && y<2*h/3) central++;
                    }
                    future.complete(new Pixels(w,h,hot,central,captureState()));
                } catch (Throwable e) { future.completeExceptionally(e); }
            });
        });
        ctx.waitFor(mc -> future.isDone());
        return future.join();
    }
    /** Failure-only experiment, never an acceptance path: distinguish shader depth matching from
     * rasterization/target errors. All production materials are restored before the test fails. */
    public static String diagnoseDepth(ClientGameTestContext ctx) {
        try {
            var field = ScopeThermalMaterial.Depth.class.getDeclaredField("pipeline");
            field.setAccessible(true);
            var scene = field.get(ScopeThermalMaterial.Depth.SCENE);
            var shifted = field.get(ScopeThermalMaterial.Depth.SHIFTED);
            try {
                StringBuilder result = new StringBuilder();
                for (boolean noDepth : new boolean[]{false, true}) {
                    ctx.runOnClient(mc -> {
                        try {
                            var original = (com.mojang.blaze3d.pipeline.RenderPipeline) field.get(ScopeThermalMaterial.Depth.NATIVE);
                            var builder = ScopeRenderTypes.clonePipeline(original, net.minecraft.resources.Identifier.parse("tacz_probe:thermal_" + noDepth));
                            if (noDepth) builder.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false);
                            var diagnostic = net.minecraft.client.renderer.RenderPipelines.register(builder.build());
                            field.set(ScopeThermalMaterial.Depth.SCENE, diagnostic);
                            field.set(ScopeThermalMaterial.Depth.SHIFTED, diagnostic);
                            ScopeThermalMaterial.clear();
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                    ctx.waitTicks(5);
                    result.append(noDepth ? " noDepth=" : " ordinaryDepth=").append(read(ctx));
                }
                return result.toString();
            } finally {
                ctx.runOnClient(mc -> {
                    try {
                        field.set(ScopeThermalMaterial.Depth.SCENE, scene);
                        field.set(ScopeThermalMaterial.Depth.SHIFTED, shifted);
                        ScopeThermalMaterial.clear();
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static void releaseAndVerify() {
        var target = ScopeThermalCapture.current();
        if (target == null) throw new AssertionError("Cannot test release without a live heat target");
        var color = target.getColorTexture();
        var depth = target.getDepthTexture();
        ScopeLensFrame.clear();
        assertCleared();
        if (!color.isClosed() || !depth.isClosed()) throw new AssertionError("Heat attachments leaked across lens clear");
    }
    public static void assertCleared() {
        if (ScopeThermalCapture.current() != null) throw new AssertionError("Stale heat attachment remained usable");
    }
    private static String captureState() {
        var state = new StringBuilder();
        try {
            for (String name : new String[]{"active", "recording", "ready", "failed", "shaderPack", "submittedModels", "texturedModels"}) {
                var field = ScopeThermalCapture.class.getDeclaredField(name);
                field.setAccessible(true);
                state.append(name).append('=').append(field.get(null)).append(' ');
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return state.toString();
    }
    private ThermalReadback() {}
}
