package com.tacz.guns.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

/** Inspect the composed world (including the test particle) and the actual foreground draw
 * on the SAME frame. Nothing in the production scene is removed, recoloured or re-rendered. */
public record OpticalLayers(BufferedImage scene, BufferedImage display) {
    private static CompletableFuture<OpticalLayers> pending;
    private static CompletableFuture<BufferedImage> sceneCopy;

    static OpticalLayers sample(ClientGameTestContext ctx, String name, boolean shader) {
        try {
            var png=ctx.takeScreenshot(name);
            if (!shader) {
                var image=ImageIO.read(png.toFile());
                return new OpticalLayers(image,image);
            }
            var future=new CompletableFuture<OpticalLayers>();
            ctx.runOnClient(mc->pending=future);
            ctx.waitFor(mc->future.isDone());
            var pair=future.join();
            ImageIO.write(pair.scene,"PNG",png.resolveSibling(name+"_scene.png").toFile());
            ImageIO.write(pair.display,"PNG",png.resolveSibling(name+"_display.png").toFile());
            return pair;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static void beforeForeground() {
        if (pending!=null) sceneCopy=SensorLayers.readColor(Minecraft.getInstance().gameRenderer.mainRenderTarget());
    }
    public static void afterForeground() {
        if (pending==null || sceneCopy==null) return;
        var future=pending; pending=null;
        sceneCopy.thenCombine(SensorLayers.readColor(Minecraft.getInstance().gameRenderer.mainRenderTarget()), OpticalLayers::new)
                .whenComplete((pair,error)->{ if (error!=null) future.completeExceptionally(error); else future.complete(pair); });
        sceneCopy=null;
    }
}
