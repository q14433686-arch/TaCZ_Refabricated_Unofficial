package com.tacz.guns.client.tuning;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.config.client.ViewTuningConfig.Shape;
import com.tacz.guns.config.client.ViewTuningConfig.TextureCenter;

import java.util.Locale;
import java.util.StringJoiner;

/** Two bounded, versioned clipboard formats. V1 field order is fixed, independent of enum ordinals. */
public enum TuningShare {
    POSITION("TACZ-POS1:"), CROSSHAIR("TACZ-HUD1:");

    private final String prefix;
    TuningShare(String prefix) { this.prefix = prefix; }
    private static final Axis[] XYZ = {Axis.X, Axis.Y, Axis.Z};
    private static final Measure[] HUD_V1 = {Measure.SIZE, Measure.LENGTH, Measure.GAP, Measure.THICKNESS,
            Measure.ASPECT, Measure.DOT_SIZE, Measure.OPACITY, Measure.SPREAD_SCALE};

    public String encode(TuningValues values) {
        var fields = new StringJoiner(",", prefix, "");
        if (this == POSITION) {
            for (Axis axis : XYZ) fields.add(String.format(Locale.ROOT, "%.3f", values.get(axis.option)));
        } else {
            fields.add(values.get(ViewTuningConfig.MODE).name()).add(values.get(RenderConfig.CROSSHAIR_TYPE).name())
                    .add(values.get(ViewTuningConfig.SHAPE).name()).add(values.get(ViewTuningConfig.TEXTURE_CENTER).name());
            int flags = (values.get(ViewTuningConfig.DOT) ? 1 : 0) | (values.get(ViewTuningConfig.OUTLINE) ? 2 : 0)
                    | (values.get(ViewTuningConfig.DYNAMIC) ? 4 : 0);
            fields.add(Integer.toString(flags)).add(String.format(Locale.ROOT, "%06X", values.get(ViewTuningConfig.RGB)));
            for (Measure measure : HUD_V1) fields.add(Integer.toString(values.get(measure.option)));
        }
        return fields.toString();
    }

    /** Wrong group/version, missing/unknown fields, non-finite or out-of-range values change nothing. */
    public boolean decode(String clipboard, TuningValues.Draft target) {
        if (clipboard == null || clipboard.length() > 256) return false;
        String code = clipboard.strip();
        if (!code.startsWith(prefix)) return false;
        String[] fields = code.substring(prefix.length()).split(",", -1);
        var parsed = new TuningValues.Draft();
        try {
            if (this == POSITION) {
                if (fields.length != XYZ.length) return false;
                for (int i = 0; i < XYZ.length; i++) {
                    if (!fields[i].matches("-?[0-9]+(?:\\.[0-9]{1,3})?")) return false;
                    double n = Double.parseDouble(fields[i]);
                    if (!(n >= XYZ[i].min && n <= XYZ[i].max)) return false;
                    parsed.set(XYZ[i].option, n);
                }
            } else {
                if (fields.length != 6 + HUD_V1.length) return false;
                parsed.set(ViewTuningConfig.MODE, Mode.valueOf(fields[0]));
                parsed.set(RenderConfig.CROSSHAIR_TYPE, CrosshairType.valueOf(fields[1]));
                parsed.set(ViewTuningConfig.SHAPE, Shape.valueOf(fields[2]));
                parsed.set(ViewTuningConfig.TEXTURE_CENTER, TextureCenter.valueOf(fields[3]));
                int flags = integer(fields[4], 0, 7);
                parsed.set(ViewTuningConfig.DOT, (flags & 1) != 0);
                parsed.set(ViewTuningConfig.OUTLINE, (flags & 2) != 0);
                parsed.set(ViewTuningConfig.DYNAMIC, (flags & 4) != 0);
                if (!fields[5].matches("[0-9a-fA-F]{6}")) return false;
                parsed.set(ViewTuningConfig.RGB, Integer.parseInt(fields[5], 16));
                for (int i = 0; i < HUD_V1.length; i++) {
                    Measure m = HUD_V1[i];
                    parsed.set(m.option, integer(fields[i + 6], m.min, m.max));
                }
            }
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        target.merge(parsed);
        return true;
    }

    private static int integer(String text, int min, int max) {
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException();
        int value = Integer.parseInt(text);
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }
}
