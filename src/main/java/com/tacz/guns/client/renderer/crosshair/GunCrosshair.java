package com.tacz.guns.client.renderer.crosshair;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.client.gameplay.ShotEnvelope;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Dimension;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.config.client.ViewTuningConfig.Shape;
import com.tacz.guns.config.client.ViewTuningConfig.TextureCenter;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** HUD and editor share one floating-point origin. Hit markers and optics have separate renderers. */
public final class GunCrosshair {
    private static final Identifier CUSTOM_PNG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/custom.png");

    public static void onLocalFire(GunFirePostEvent event) {
        if (!event.getLogicalSide().isClient()) return;
        var player = Minecraft.getInstance().player;
        // Tracking packets can post this event for OTHER players. A dry/cancelled shot doesn't post it at all.
        if (player != null && event.getShooter() == player) {
            var style = TuningProfiles.forStack(event.getGunItemStack());
            if (!style.get(ViewTuningConfig.DYNAMIC)) return;
            IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder().hudShotEnvelope
                    .fire(GunTime.nowMillis(), style.get(Measure.RECOVERY.option));
        }
    }

    public static void draw(GuiGraphics gui, float x, float y, TuningValues values) {
        draw(gui, x, y, values, Integer.MAX_VALUE, 0);
    }

    /** Only the editor supplies a fit limit and an optional preview-only shot. */
    public static void draw(GuiGraphics gui, float x, float y, TuningValues v, int fitRadius, float previewShot) {
        int opacity = v.get(Measure.OPACITY.option);
        if (opacity == 0) return;
        int color = opacity << 24 | v.get(ViewTuningConfig.RGB);
        boolean dynamic = v.get(ViewTuningConfig.DYNAMIC);
        float spread = 0, reserve = 0;
        if (dynamic) {
            var mc = Minecraft.getInstance();
            float shot = fitRadius != Integer.MAX_VALUE || mc.player == null ? 0 : IClientPlayerGunOperator.fromLocalPlayer(mc.player)
                    .getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis());
            float kick = dim(v, Dimension.KICK), level = Math.max(shot, previewShot);
            spread = GunSpread.radius(mc, v.get(Measure.SPREAD_SCALE.option)) + level * kick;
            // Reserve the full excursion when fitting the editor. Otherwise large shapes stay pressed
            // against the card edge and the center dot appears to shrink instead of the gap opening.
            if (fitRadius != Integer.MAX_VALUE) reserve = (ShotEnvelope.MAX_SHOTS - level) * kick;
        }
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        float aspect = v.get(Measure.ASPECT.option) / 100f;
        if (v.get(ViewTuningConfig.MODE) == Mode.DESIGNER) designer(gui, v, aspect, spread, reserve, dynamic, color, fitRadius);
        else image(gui, v, aspect, spread, reserve, dynamic, color, fitRadius);
        gui.pose().popMatrix();
    }

    private static float dim(TuningValues v, Dimension d) { return v.get(d.option).floatValue(); }

    private static void designer(GuiGraphics gui, TuningValues v, float aspect, float spread, float reserve, boolean dynamic, int color, int fitRadius) {
        Shape shape = v.get(ViewTuningConfig.SHAPE);
        float t = dim(v, Dimension.THICKNESS), dot = dim(v, Dimension.DOT_SIZE);
        dot = v.get(ViewTuningConfig.DOT) ? (dot == 0 ? t : dot) : 0;
        int black = color & 0xFF000000;
        boolean outline = v.get(ViewTuningConfig.OUTLINE);
        if (shape == Shape.RING) {
            // Radius measures the clear hole; changing stroke or dot size does not move its boundary.
            float radius = dim(v, Dimension.RADIUS), ix = radius + spread, iy = radius * aspect + spread;
            float ox = ix + t, oy = iy + t;
            fit(gui, Math.max(Math.max(ox, oy) + reserve, dot / 2) + 1, fitRadius);
            if (outline) {
                RingBand.draw(gui, ox, oy, ox + 1, oy + 1, black);
                RingBand.draw(gui, Math.max(0, ix - 1), Math.max(0, iy - 1), ix, iy, black);
            }
            RingBand.draw(gui, ix, iy, ox, oy, color);
        } else {
            float len = dim(v, Dimension.LENGTH), vertical = len * aspect;
            if (len == 0 && dot > 0 && dynamic) {
                shape = Shape.CROSS; len = vertical = 2; t = 1;
            }
            // Negative gap pulls arms into the central core. Stop at the axis, never swap the arms.
            float inner = Math.max(0, Math.max(t, dot) / 2 + dim(v, Dimension.GAP)) + spread;
            float depth = shape == Shape.LADDER ? 3 * Math.max(vertical, t) + t / 2 : vertical;
            fit(gui, Math.max(dot / 2, inner + reserve + Math.max(len, depth)) + 1, fitRadius);
            if (len > 0) {
                if (outline) lines(gui, shape, inner, len, vertical, t, black, true);
                lines(gui, shape, inner, len, vertical, t, color, false);
            }
        }
        if (dot > 0) {
            if (outline) part(gui, -dot / 2, -dot / 2, dot, dot, black, true);
            part(gui, -dot / 2, -dot / 2, dot, dot, color, false);
        }
    }

    private static void image(GuiGraphics gui, TuningValues v, float aspect, float spread, float reserve, boolean dynamic, int color, int fitRadius) {
        boolean preset = v.get(ViewTuningConfig.MODE) == Mode.PRESET;
        var type = v.get(RenderConfig.CROSSHAIR_TYPE);
        if (preset && type == CrosshairType.EMPTY) return;
        boolean dotPreset = preset && type == CrosshairType.DOT_1;
        float size = dim(v, Dimension.SIZE), dot = dim(v, Dimension.DOT_SIZE);
        if (dot == 0) dot = 1;
        float grow = dotPreset ? 0 : 2 * spread, w = size + grow, h = size * aspect + grow;
        TextureCenter center = v.get(ViewTuningConfig.TEXTURE_CENTER);
        // The authored DOT_1 center covers 10 of its 128 texels; don't bury ticks inside a large preset.
        float core = center == TextureCenter.DOT ? dot : center == TextureCenter.ORIGINAL && dotPreset ? Math.max(w, h) * (10f / 128) : 0;
        float inner = core / 2 + spread;
        float extent = Math.max(Math.max(w, h) / 2 + (dotPreset ? 0 : reserve), core / 2);
        fit(gui, dotPreset && dynamic ? Math.max(extent, inner + reserve + 2) : extent, fitRadius);
        texture(gui, preset ? CrosshairType.getTextureLocation(type) : CUSTOM_PNG, w, h, center, color);
        if (center == TextureCenter.DOT) part(gui, -dot / 2, -dot / 2, dot, dot, color, false);
        if (dotPreset && dynamic) lines(gui, Shape.CROSS, inner, 2, 2, 1, color, false);
    }

    private static void fit(GuiGraphics gui, float extent, int available) {
        if (extent > available) gui.pose().scale(available / extent, available / extent);
    }

    private static void lines(GuiGraphics gui, Shape shape, float inner, float len, float vertical, float t, int color, boolean border) {
        if (shape == Shape.BOX) {
            float x = inner + len, y = inner + vertical, stroke = Math.min(t, Math.min(x, y));
            if (border) {
                frame(gui, -x - 1, -y - 1, 2*x + 2, 2*y + 2, 1, color);
                frame(gui, -x + stroke, -y + stroke, 2*(x - stroke), 2*(y - stroke), 1, color);
            } else frame(gui, -x, -y, 2*x, 2*y, stroke, color);
        } else {
            part(gui, -inner - len, -t / 2, len, t, color, border);
            part(gui, inner, -t / 2, len, t, color, border);
            if (shape == Shape.CROSS) part(gui, -t / 2, -inner - vertical, t, vertical, color, border);
            if (shape == Shape.LADDER) {
                // A decorative game HUD: three unnumbered rungs below the fixed center. No range or trajectory input.
                float step = Math.max(vertical, t), from = inner;
                for (int i = 1; i <= 3; i++) {
                    float y = inner + i * step, half = Math.max(t / 2, len * (i + 1) / 4);
                    part(gui, -t / 2, from, t, y - t / 2 - from, color, border);
                    part(gui, -half, y - t / 2, 2 * half, t, color, border);
                    from = y + t / 2;
                }
            } else if (shape != Shape.SIDES) part(gui, -t / 2, inner, t, vertical, color, border);
        }
    }

    private static void part(GuiGraphics gui, float x, float y, float w, float h, int color, boolean border) {
        if (border) frame(gui, x - 1, y - 1, w + 2, h + 2, 1, color);
        else rect(gui, x, y, w, h, color);
    }

    private static void frame(GuiGraphics gui, float x, float y, float w, float h, float stroke, int color) {
        if (w <= 0 || h <= 0) return;
        if (2 * stroke >= Math.min(w, h)) { rect(gui, x, y, w, h, color); return; }
        // Four non-overlapping bands: translucent corners have the same opacity as their sides.
        rect(gui, x, y, w, stroke, color);
        rect(gui, x, y + h - stroke, w, stroke, color);
        rect(gui, x, y + stroke, stroke, h - 2*stroke, color);
        rect(gui, x + w - stroke, y + stroke, stroke, h - 2*stroke, color);
    }

    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y).scale(w, h);
        gui.fill(0, 0, 1, 1, color);
        gui.pose().popMatrix();
    }

    private static void texture(GuiGraphics gui, Identifier tex, float w, float h, TextureCenter center, int color) {
        // The full UV square is exactly [-w/2,w/2] x [-h/2,h/2] for ANY fractional dimensions.
        gui.pose().pushMatrix();
        gui.pose().scale(w / 16, h / 16);
        if (center == TextureCenter.ORIGINAL) tile(gui, tex, 0, 0, 16, 16, color);
        else {
            tile(gui, tex, 0, 0, 16, 6, color); tile(gui, tex, 0, 10, 16, 6, color);
            tile(gui, tex, 0, 6, 6, 4, color); tile(gui, tex, 10, 6, 6, 4, color);
        }
        gui.pose().popMatrix();
    }

    private static void tile(GuiGraphics gui, Identifier tex, int u, int v, int w, int h, int color) {
        gui.blit(RenderPipelines.GUI_TEXTURED, tex, u - 8, v - 8, u, v, w, h, w, h, 16, 16, color);
    }

    private GunCrosshair() { }
}
