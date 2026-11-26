package com.tacz.probe;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/** Test-only readback at a specific render boundary, never a replacement renderer. */
final class OpticsReadback {
    static CompletableFuture<BufferedImage> readColor(RenderTarget target) {
        var result=new CompletableFuture<BufferedImage>();
        Screenshot.takeScreenshot(target,pixels -> {
            try(pixels) {
                var image=new BufferedImage(pixels.getWidth(),pixels.getHeight(),BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) image.setRGB(x,y,pixels.getPixel(x,y));
                result.complete(image);
            } catch(Throwable e) { result.completeExceptionally(e); }
        });
        return result;
    }
    private OpticsReadback() {}
}
