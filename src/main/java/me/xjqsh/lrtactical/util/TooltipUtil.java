package me.xjqsh.lrtactical.util;

import java.util.Locale;

public final class TooltipUtil {
    private TooltipUtil() {
    }

    public static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String formatFactor(float factor) {
        return String.format(Locale.ROOT, "x%.2f", factor);
    }

    public static String formatTicks(int ticks) {
        if (ticks % 20 == 0) {
            return ticks / 20 + "s";
        }
        return String.format(Locale.ROOT, "%.2fs", ticks / 20.0);
    }
}
