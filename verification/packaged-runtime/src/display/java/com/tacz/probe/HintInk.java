package com.tacz.probe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Calibrate the TARGET font's RGB, not a 26.x PNG literal or the screenshot's alpha channel.
 * This tiny reference is at top left, on a separate frame, and disabled for ALL measured frames. */
public final class HintInk implements ClientModInitializer {
    private static boolean reference;
    private static int color;
    @Override public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.parse("tacz_packaged_probe:font_reference"), (gui, dt) -> {
            if (reference) {
                gui.fill(0, 0, 20, 16, 0xff000000);
                gui.drawString(Minecraft.getInstance().font, "HH", 2, 2, 0xffffff55, false);
            }
        });
    }
    static void calibrate(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> reference = true);
        try {
            var im = ClientProbe.read(ctx.takeScreenshot("rack_font_reference"));
            int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            Map<Integer, Integer> hist = new HashMap<>();
            for (int x = 0; x < 20 * scale; x++) for (int y = 0; y < 16 * scale; y++) {
                int rgb = im.getRGB(x,y) & 0xffffff, r = rgb>>16, g = rgb>>8 & 255, b = rgb & 255;
                if (r > 160 && g > 160 && Math.abs(r-g) < 32 && b < Math.min(r,g)-70) hist.merge(rgb, 1, Integer::sum);
            }
            var swatch = hist.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow(() -> new AssertionError("Missing native yellow font reference"));
            if (swatch.getValue() < 12 * scale) throw new AssertionError("Too few calibration pixels: " + swatch);
            color = swatch.getKey();
            System.out.printf("PORT_INK: actual 1.21.11 HUD font #%06x, %d pixels%n", color, swatch.getValue());
        } finally { ctx.runOnClient(mc -> reference = false); }
    }
    static boolean matches(int pixel) {
        return Math.abs((pixel>>16 & 255) - (color>>16)) <= 2 && Math.abs((pixel>>8 & 255) - (color>>8 & 255)) <= 2
                && Math.abs((pixel & 255) - (color & 255)) <= 2;
    }
}
