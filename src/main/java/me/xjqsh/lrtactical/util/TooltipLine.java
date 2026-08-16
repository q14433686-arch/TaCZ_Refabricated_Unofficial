package me.xjqsh.lrtactical.util;

import net.minecraft.network.chat.Component;

/** A data-driven LRTactical tooltip line. */
public record TooltipLine(Component text, boolean collapsible) {
    public static TooltipLine normal(Component text) {
        return new TooltipLine(text, false);
    }

    public static TooltipLine collapsible(Component text) {
        return new TooltipLine(text, true);
    }
}
