package com.tacz.guns.client.renderer.crosshair;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.config.client.ViewTuningConfig.Shape;
import com.tacz.guns.config.client.ViewTuningConfig.TextureCenter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Shared by the HUD (after its visibility checks) and the settings preview. Does not draw hit markers or scope reticles. */
public final class GunCrosshair {
    private static final Identifier CUSTOM_PNG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/custom.png");

    public static void draw(GuiGraphicsExtractor gui, int x, int y, TuningValues values) {
        draw(gui, x, y, values, Integer.MAX_VALUE);
    }

    /** Only the editor passes a size limit. Shrink very large previews to fit, never magnify small ones. */
    public static void draw(GuiGraphicsExtractor gui, int x, int y, TuningValues values, int fitRadius) {
        int opacity = values.get(Measure.OPACITY.option);
        if (opacity == 0) return;
        int color = opacity << 24 | values.get(ViewTuningConfig.RGB);
        Mode mode = values.get(ViewTuningConfig.MODE);
        int spread = values.get(ViewTuningConfig.DYNAMIC) ? GunSpread.radius(Minecraft.getInstance(), values.get(Measure.SPREAD_SCALE.option)) : 0;
        int aspect = values.get(Measure.ASPECT.option), dot = values.get(Measure.DOT_SIZE.option);
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        if (mode != Mode.DESIGNER) {
            int size = values.get(Measure.SIZE.option), w = size + 2 * spread, h = scaled(size, aspect) + 2 * spread;
            fit(gui, (Math.max(Math.max(w, h), dot) + 1) / 2, fitRadius);
            var preset = values.get(RenderConfig.CROSSHAIR_TYPE);
            Identifier texture = mode == Mode.PNG ? CUSTOM_PNG : CrosshairType.getTextureLocation(preset);
            TextureCenter center = mode == Mode.PRESET && preset == CrosshairType.EMPTY ? TextureCenter.ORIGINAL : values.get(ViewTuningConfig.TEXTURE_CENTER);
            texture(gui, texture, w, h, center, dot == 0 ? 1 : dot, color);
        } else {
            int t = values.get(Measure.THICKNESS.option), gap = values.get(Measure.GAP.option) + spread;
            int len = values.get(Measure.LENGTH.option), vertical = scaled(len, aspect);
            dot = values.get(ViewTuningConfig.DOT) ? (dot == 0 ? t : dot) : 0;
            int core = Math.max(t, dot), low = -core / 2 - gap, high = core - core / 2 + gap;
            fit(gui, high + Math.max(len, vertical) + 1, fitRadius);
            Shape shape = values.get(ViewTuningConfig.SHAPE);
            if (values.get(ViewTuningConfig.OUTLINE)) lines(gui, shape, low, high, len, vertical, t, dot, opacity << 24, true);
            lines(gui, shape, low, high, len, vertical, t, dot, color, false);
        }
        gui.pose().popMatrix();
    }

    private static int scaled(int n, int percent) { return n == 0 ? 0 : Math.max(1, (n * percent + 50) / 100); }

    private static void fit(GuiGraphicsExtractor gui, int radius, int available) {
        if (radius > available) gui.pose().scale((float) available / radius, (float) available / radius);
    }

    private static void lines(GuiGraphicsExtractor gui, Shape shape, int low, int high, int len, int vertical, int t, int dot, int color, boolean border) {
        int start = -t / 2;
        if (len > 0 && shape == Shape.BOX) {
            int x = low - len, y = low - vertical, w = high - low + 2 * len, h = high - low + 2 * vertical;
            // Four non-overlapping bands, so translucent corners don't become darker.
            int stroke = Math.min(t, Math.min(w, h) / 2);
            if (border) {
                gui.outline(x - 1, y - 1, w + 2, h + 2, color);
                if (w > 2 * stroke && h > 2 * stroke) gui.outline(x + stroke, y + stroke, w - 2 * stroke, h - 2 * stroke, color);
            } else {
                gui.fill(x, y, x + w, y + stroke, color);
                gui.fill(x, y + h - stroke, x + w, y + h, color);
                gui.fill(x, y + stroke, x + stroke, y + h - stroke, color);
                gui.fill(x + w - stroke, y + stroke, x + w, y + h - stroke, color);
            }
        } else if (len > 0) {
            part(gui, low - len, start, len, t, color, border);
            part(gui, high, start, len, t, color, border);
            if (shape == Shape.CROSS) part(gui, start, low - vertical, t, vertical, color, border);
            if (shape != Shape.SIDES) part(gui, start, high, t, vertical, color, border);
        }
        if (dot > 0) part(gui, -dot / 2, -dot / 2, dot, dot, color, border);
    }

    private static void part(GuiGraphicsExtractor gui, int x, int y, int w, int h, int color, boolean border) {
        if (border) gui.outline(x - 1, y - 1, w + 2, h + 2, color);
        else gui.fill(x, y, x + w, y + h, color);
    }

    private static void texture(GuiGraphicsExtractor gui, Identifier tex, int w, int h, TextureCenter center, int dot, int color) {
        int x = -w / 2, y = -h / 2;
        if (center == TextureCenter.ORIGINAL) {
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, w, h, 16, 16, 16, 16, color);
            return;
        }
        // Omit the center UV square, not a background-colored fill (the world must show through it).
        // Use normalized sixteenths for both legacy textures and arbitrary-resolution resource-pack PNGs.
        int left = w * 6 / 16, right = w - left, top = h * 6 / 16, bottom = h - top;
        if (top > 0) {
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, w, top, 16, 6, 16, 16, color);
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + bottom, 0, 10, w, top, 16, 6, 16, 16, color);
        }
        if (left > 0) {
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + top, 0, 6, left, bottom - top, 6, 4, 16, 16, color);
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, x + right, y + top, 10, 6, left, bottom - top, 6, 4, 16, 16, color);
        }
        if (center == TextureCenter.DOT) part(gui, -dot / 2, -dot / 2, dot, dot, color, false);
    }

    private GunCrosshair() { }
}
