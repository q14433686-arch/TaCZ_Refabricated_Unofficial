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
