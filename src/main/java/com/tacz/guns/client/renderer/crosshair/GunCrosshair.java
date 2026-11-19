package com.tacz.guns.client.renderer.crosshair;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Shared by the HUD (after its visibility checks) and the settings preview. Does not draw hit markers or scope reticles. */
public final class GunCrosshair {
    private static final Identifier CUSTOM_PNG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/custom.png");

    public static void draw(GuiGraphicsExtractor gui, int x, int y, TuningValues values) {
        int opacity = values.get(Measure.OPACITY.option);
        if (opacity == 0) return;
        int color = opacity << 24 | values.get(ViewTuningConfig.RGB);
        Mode mode = values.get(ViewTuningConfig.MODE);
        if (mode != Mode.DESIGNER) {
            int size = values.get(Measure.SIZE.option);
            Identifier texture = mode == Mode.PNG ? CUSTOM_PNG : CrosshairType.getTextureLocation(values.get(RenderConfig.CROSSHAIR_TYPE));
            // Whole UV range, including for a resource pack image whose resolution is not 16x16.
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x - size / 2, y - size / 2, 0, 0, size, size, 16, 16, 16, 16, color);
            return;
        }
        int thickness = values.get(Measure.THICKNESS.option);
        int left = x - thickness / 2, top = y - thickness / 2;
        int gap = values.get(Measure.GAP.option), length = values.get(Measure.LENGTH.option);
        boolean dot = values.get(ViewTuningConfig.DOT);
        if (values.get(ViewTuningConfig.OUTLINE)) {
            shape(gui, left, top, thickness, gap, length, dot, opacity << 24, true);
        }
        shape(gui, left, top, thickness, gap, length, dot, color, false);
    }

    private static void shape(GuiGraphicsExtractor gui, int x, int y, int t, int gap, int len, boolean dot, int color, boolean border) {
        if (len > 0) {
            part(gui, x - gap - len, y, len, t, color, border);
            part(gui, x + t + gap, y, len, t, color, border);
            part(gui, x, y - gap - len, t, len, color, border);
            part(gui, x, y + t + gap, t, len, color, border);
        }
        if (dot) part(gui, x, y, t, t, color, border);
    }

    private static void part(GuiGraphicsExtractor gui, int x, int y, int w, int h, int color, boolean border) {
        if (border) gui.outline(x - 1, y - 1, w + 2, h + 2, color);
        else gui.fill(x, y, x + w, y + h, color);
    }

    private GunCrosshair() { }
}
