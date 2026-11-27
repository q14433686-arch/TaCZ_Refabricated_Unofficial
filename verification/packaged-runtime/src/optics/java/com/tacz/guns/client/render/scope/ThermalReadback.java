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
                for (int mode : new int[]{0, 1, 2}) {
                    ctx.runOnClient(mc -> {
                        try {
                            var original = (com.mojang.blaze3d.pipeline.RenderPipeline) field.get(ScopeThermalMaterial.Depth.NATIVE);
                            var builder = ScopeRenderTypes.clonePipeline(original, net.minecraft.resources.Identifier.parse("tacz_probe:thermal_" + mode));
                            if (mode > 0) builder.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false);
                            if (mode == 2) builder.withFragmentShader(net.minecraft.resources.Identifier.parse("tacz_probe:core/thermal_depth"));
                            var diagnostic = builder.build();
                            field.set(ScopeThermalMaterial.Depth.SCENE, diagnostic);
                            field.set(ScopeThermalMaterial.Depth.SHIFTED, diagnostic);
                            ScopeThermalMaterial.clear();
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                    ctx.waitTicks(5);
                    if (mode == 2) result.append(" windowDepth=").append(readDepth(ctx));
                    else result.append(mode == 1 ? " noDepth=" : " ordinaryDepth=").append(read(ctx));
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
    private static String readDepth(ClientGameTestContext ctx) {
        var future = new CompletableFuture<String>();
        ctx.runOnClient(mc -> {
            var target = ScopeThermalCapture.current();
            if (target == null) throw new AssertionError("No diagnostic depth target");
            float[] mask = depth(target.getDepthTexture(), target.width, target.height);
            var main = mc.getMainRenderTarget();
            float[] scene = depth(main.getDepthTexture(), main.width, main.height);
            int mid = target.height / 2 * target.width + target.width / 2;
            Screenshot.takeScreenshot(target, pixels -> {
                try (pixels) {
                    int rgb = pixels.getPixel(pixels.getWidth()/2,pixels.getHeight()/2);
                    double z = ((rgb>>16)&255)/255d + ((rgb>>8)&255)/65025d + (rgb&255)/16581375d;
                    future.complete("replay="+z+" rgb="+Integer.toHexString(rgb)+" private="+mask[mid]+" mainAfterHand="+scene[mid]);
                } catch (Throwable e) { future.completeExceptionally(e); }
            });
        });
        ctx.waitFor(mc -> future.isDone());
        return future.join();
    }
    private static float[] depth(com.mojang.blaze3d.textures.GpuTexture texture,int w,int h) {
        int[] keys = {org.lwjgl.opengl.GL45C.GL_PACK_SKIP_PIXELS,org.lwjgl.opengl.GL45C.GL_PACK_SKIP_ROWS,
                org.lwjgl.opengl.GL45C.GL_PACK_ROW_LENGTH,org.lwjgl.opengl.GL45C.GL_PACK_ALIGNMENT};
        int[] old = java.util.Arrays.stream(keys).map(org.lwjgl.opengl.GL45C::glGetInteger).toArray();
        try {
            int id = (Integer) texture.getClass().getMethod("iris$getGlId").invoke(texture);
            for(int i=0;i<keys.length;i++) org.lwjgl.opengl.GL45C.glPixelStorei(keys[i],i==3?4:0);
            float[] pixels = new float[w*h];
            org.lwjgl.opengl.GL45C.glGetTextureImage(id,0,org.lwjgl.opengl.GL45C.GL_DEPTH_COMPONENT,org.lwjgl.opengl.GL45C.GL_FLOAT,pixels);
            return pixels;
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        finally { for(int i=0;i<keys.length;i++)org.lwjgl.opengl.GL45C.glPixelStorei(keys[i],old[i]); }
    }
    /** Test-only failure injection: exercise the production fail-open lens branch, not a
     * disabled-feature substitute for a working positive. Callers restore in finally. */
    public static void forceUnavailable(boolean value) {
        try {
            var field = ScopeThermalCapture.class.getDeclaredField("failed");
            field.setAccessible(true);
            field.setBoolean(null,value);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
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
