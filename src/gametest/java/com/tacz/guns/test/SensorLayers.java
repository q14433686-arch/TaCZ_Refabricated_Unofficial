package com.tacz.guns.test;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/** Test-only GPU readbacks of one actual composite: separate heat from white reticle/REC/range graphics. */
public record SensorLayers(BufferedImage sensor, int heatPixels) {
    private static CompletableFuture<SensorLayers> pending;

    static SensorLayers sample(ClientGameTestContext ctx) {
        var future = new CompletableFuture<SensorLayers>();
        ctx.runOnClient(mc -> pending = future);
        ctx.waitFor(mc -> future.isDone());
        return future.join();
    }

    public static boolean requested() { return pending != null; }
    public static void observe(RenderTarget main, RenderTarget mask) {
        var future = pending; pending = null;
        if (mask == null) { future.completeExceptionally(new AssertionError("Thermal replay not ready")); return; }
        var heat = new CompletableFuture<Integer>();
        Screenshot.takeScreenshot(mask, pixels -> {
            try (pixels) {
                int n=0;
                for (int y=0;y<pixels.getHeight();y++) for (int x=0;x<pixels.getWidth();x++)
                    if ((pixels.getPixel(x,y)&255)>127) n++;
                heat.complete(n);
            } catch (Throwable e) { heat.completeExceptionally(e); }
        });
        Screenshot.takeScreenshot(main, pixels -> {
            try (pixels) {
                var image = new BufferedImage(pixels.getWidth(),pixels.getHeight(),BufferedImage.TYPE_INT_RGB);
                for (int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) image.setRGB(x,y,pixels.getPixel(x,y));
                heat.whenComplete((n,e)->{ if (e!=null) future.completeExceptionally(e); else future.complete(new SensorLayers(image,n)); });
            } catch (Throwable e) { future.completeExceptionally(e); }
        });
    }
}
