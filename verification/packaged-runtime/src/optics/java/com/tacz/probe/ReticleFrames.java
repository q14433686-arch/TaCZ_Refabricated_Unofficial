package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Actual ADS frames, removing one material at a time from the same loaded default model.
 * No production debug toggle or replacement model; every mutation is restored in finally. */
final class ReticleFrames {
    static void verify(ClientGameTestContext ctx, Identifier optic) {
        Map<BedrockPart, List<BedrockCube>> ink = new IdentityHashMap<>(), light = new IdentityHashMap<>();
        ctx.runOnClient(mc -> {
            var model = TimelessAPI.getClientAttachmentIndex(optic).orElseThrow().getAttachmentModel();
            for (String root : List.of("division", "division_2")) collect(model.getNode(root), false, ink, light);
            if (ink.isEmpty() || light.isEmpty()) throw new AssertionError("Default hybrid fixture has no ink/luminous geometry");
        });
        try {
            var full = frame(ctx, "reticle_full");
            ctx.runOnClient(mc -> ink.keySet().forEach(p -> p.cubes.clear()));
            var luminousOnly = frame(ctx, "reticle_luminous_only");
            ctx.runOnClient(mc -> light.keySet().forEach(p -> p.cubes.clear()));
            var none = frame(ctx, "reticle_none");
            ctx.runOnClient(mc -> { restore(ink); restore(light); });
            var restored = frame(ctx, "reticle_restored");
            int inkPixels = darker(full, luminousOnly), restoredInk = darker(restored, luminousOnly);
            int lightPixels = redder(luminousOnly, none);
            if (inkPixels < 8 || restoredInk < 8 || lightPixels < 1)
                throw new AssertionError("Missing material pixels: ink=" + inkPixels + " restoredInk=" + restoredInk + " luminous=" + lightPixels);
            System.out.println("PORT_RETICLE_PIXELS: default " + optic + " ink=" + inkPixels
                    + " restoredInk=" + restoredInk + " luminous=" + lightPixels + "; independent material removal and restore");
        } finally {
            ctx.runOnClient(mc -> { restore(ink); restore(light); });
        }
    }
    private static void collect(BedrockPart part, boolean inherited, Map<BedrockPart, List<BedrockCube>> ink,
                                Map<BedrockPart, List<BedrockCube>> light) {
        if (part == null) return;
        boolean luminous = inherited || part.illuminated;
        if (!part.cubes.isEmpty()) (luminous ? light : ink).put(part, List.copyOf(part.cubes));
        for (var child : part.children) collect(child, luminous, ink, light);
    }
    private static void restore(Map<BedrockPart, List<BedrockCube>> parts) {
        parts.forEach((part, cubes) -> { part.cubes.clear(); part.cubes.addAll(cubes); });
    }
    private static BufferedImage frame(ClientGameTestContext ctx, String name) {
        ctx.waitTicks(4); // drain immediate and deferred immutable snapshots
        try { return ImageIO.read(ctx.takeScreenshot("tacz_" + name).toFile()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static int darker(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int y = a.getHeight()/3; y < a.getHeight()*2/3; y++)
            for (int x = a.getWidth()/3; x < a.getWidth()*2/3; x++) {
                int p = a.getRGB(x,y), q = b.getRGB(x,y);
                if (r(q)+g(q)+blue(q) - r(p)-g(p)-blue(p) > 100) count++;
            }
        return count;
    }
    private static int redder(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int y = a.getHeight()/3; y < a.getHeight()*2/3; y++)
            for (int x = a.getWidth()/3; x < a.getWidth()*2/3; x++) {
                int p = a.getRGB(x,y), q = b.getRGB(x,y);
                if (r(p) > 100 && r(p)-g(p) > 45 && r(p)-blue(p) > 35 && r(p)-r(q) > 25) count++;
            }
        return count;
    }
    private static int r(int p) { return p>>16 & 255; }
    private static int g(int p) { return p>>8 & 255; }
    private static int blue(int p) { return p & 255; }
    private ReticleFrames() {}
}
