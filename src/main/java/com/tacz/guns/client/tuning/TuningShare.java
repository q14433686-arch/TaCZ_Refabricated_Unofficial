package com.tacz.guns.client.tuning;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Dimension;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.config.client.ViewTuningConfig.Shape;
import com.tacz.guns.config.client.ViewTuningConfig.TextureCenter;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/** Fixed field order (not enum ordinals). Only the HUD format changed; HUD1 is still importable. */
public enum TuningShare {
    POSITION("TACZ-POS1:"), CROSSHAIR("TACZ-HUD2:");

    private final String prefix;
    TuningShare(String prefix) { this.prefix = prefix; }
    private static final String HUD1 = "TACZ-HUD1:";
    private static final Pattern FIXED_2 = Pattern.compile("-?[0-9]+(?:\\.[0-9]{1,2})?");
    private static final Axis[] XYZ = {Axis.X, Axis.Y, Axis.Z};

    public String encode(TuningValues values) {
        var f = new StringJoiner(",", prefix, "");
        if (this == POSITION) {
            for (Axis axis : XYZ) f.add(String.format(Locale.ROOT, "%.3f", values.get(axis.option)));
        } else {
            f.add(values.get(ViewTuningConfig.MODE).name()).add(values.get(RenderConfig.CROSSHAIR_TYPE).name())
                    .add(values.get(ViewTuningConfig.SHAPE).name()).add(values.get(ViewTuningConfig.TEXTURE_CENTER).name());
            int flags = (values.get(ViewTuningConfig.DOT) ? 1 : 0) | (values.get(ViewTuningConfig.OUTLINE) ? 2 : 0)
                    | (values.get(ViewTuningConfig.DYNAMIC) ? 4 : 0);
            f.add(Integer.toString(flags)).add(String.format(Locale.ROOT, "%06X", values.get(ViewTuningConfig.RGB)));
            f.add(number(values, Dimension.SIZE)).add(number(values, Dimension.LENGTH)).add(number(values, Dimension.GAP))
                    .add(number(values, Dimension.THICKNESS)).add(values.get(Measure.ASPECT.option).toString())
                    .add(number(values, Dimension.DOT_SIZE)).add(values.get(Measure.OPACITY.option).toString())
                    .add(values.get(Measure.SPREAD_SCALE.option).toString()).add(number(values, Dimension.RADIUS))
                    .add(number(values, Dimension.KICK)).add(values.get(Measure.RECOVERY.option).toString());
        }
        return f.toString();
    }

    private static String number(TuningValues values, Dimension d) { return String.format(Locale.ROOT, "%.2f", values.get(d.option)); }

    /** Validate into a scratch draft so even a bad final field modifies nothing. */
    public boolean decode(String clipboard, TuningValues.Draft target) {
        if (clipboard == null || clipboard.length() > 256) return false;
        String code = clipboard.strip();
        boolean v1 = this == CROSSHAIR && code.startsWith(HUD1);
        if (!v1 && !code.startsWith(prefix)) return false;
        String[] f = code.substring(v1 ? HUD1.length() : prefix.length()).split(",", -1);
        var parsed = new TuningValues.Draft();
        try {
            if (this == POSITION) {
                if (f.length != XYZ.length) return false;
                for (int i = 0; i < XYZ.length; i++) {
                    if (!f[i].matches("-?[0-9]+(?:\\.[0-9]{1,3})?")) return false;
                    double n = Double.parseDouble(f[i]);
                    if (!(n >= XYZ[i].min && n <= XYZ[i].max)) return false;
                    parsed.set(XYZ[i].option, n);
                }
            } else {
                if (f.length != (v1 ? 14 : 17)) return false;
                parsed.set(ViewTuningConfig.MODE, Mode.valueOf(f[0]));
                parsed.set(RenderConfig.CROSSHAIR_TYPE, CrosshairType.valueOf(f[1]));
                var shape = Shape.valueOf(f[2]);
                if (v1 && (shape == Shape.RING || shape == Shape.LADDER)) return false;
                parsed.set(ViewTuningConfig.SHAPE, shape);
                parsed.set(ViewTuningConfig.TEXTURE_CENTER, TextureCenter.valueOf(f[3]));
                int flags = integer(f[4], 0, 7);
                parsed.set(ViewTuningConfig.DOT, (flags & 1) != 0);
                parsed.set(ViewTuningConfig.OUTLINE, (flags & 2) != 0);
                parsed.set(ViewTuningConfig.DYNAMIC, (flags & 4) != 0);
                if (!f[5].matches("[0-9a-fA-F]{6}")) return false;
                parsed.set(ViewTuningConfig.RGB, Integer.parseInt(f[5], 16));
                dimension(parsed, Dimension.SIZE, f[6], v1, 4, 64);
                dimension(parsed, Dimension.LENGTH, f[7], v1, 0, 24);
                dimension(parsed, Dimension.GAP, f[8], v1, 0, 16);
                dimension(parsed, Dimension.THICKNESS, f[9], v1, 1, 8);
                parsed.set(Measure.ASPECT.option, v1 ? integer(f[10], 25, 200) : measure(Measure.ASPECT, f[10]));
                dimension(parsed, Dimension.DOT_SIZE, f[11], v1, 0, 12);
                parsed.set(Measure.OPACITY.option, measure(Measure.OPACITY, f[12]));
                parsed.set(Measure.SPREAD_SCALE.option, v1 ? integer(f[13], 25, 200) : measure(Measure.SPREAD_SCALE, f[13]));
                if (v1) {
                    parsed.reset(Dimension.RADIUS.option); parsed.reset(Dimension.KICK.option); parsed.reset(Measure.RECOVERY.option);
                } else {
                    parsed.set(Dimension.RADIUS.option, decimal(Dimension.RADIUS, f[14]));
                    parsed.set(Dimension.KICK.option, decimal(Dimension.KICK, f[15]));
                    parsed.set(Measure.RECOVERY.option, measure(Measure.RECOVERY, f[16]));
                }
            }
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        target.merge(parsed);
        return true;
    }

    private static void dimension(TuningValues.Draft target, Dimension d, String text, boolean legacy, int min1, int max1) {
        target.set(d.option, legacy ? integer(text, min1, max1) : decimal(d, text));
    }
    private static double decimal(Dimension d, String text) {
        if (!FIXED_2.matcher(text).matches()) throw new IllegalArgumentException();
        double value = Double.parseDouble(text);
        if (!(value >= d.min && value <= d.max)) throw new IllegalArgumentException();
        return value;
    }
    private static int measure(Measure m, String text) { return integer(text, m.min, m.max); }
    private static int integer(String text, int min, int max) {
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException();
        int value = Integer.parseInt(text);
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }
}
