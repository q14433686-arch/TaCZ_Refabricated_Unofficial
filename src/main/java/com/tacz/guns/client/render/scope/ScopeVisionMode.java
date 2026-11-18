package com.tacz.guns.client.render.scope;

/** Per-attachment imaging presets for the existing scope window. Order matches scope_lens.glsl. */
public enum ScopeVisionMode {
    NORMAL,
    NIGHT_VISION_GREEN,
    NIGHT_VISION_WHITE,
    THERMAL_WHITE_HOT,
    THERMAL_BLACK_HOT,
    THERMAL_IRONBOW;

    public String tooltip() {
        return switch (this) {
            case THERMAL_WHITE_HOT -> "tooltip.tacz.attachment.thermal_imaging.white_hot";
            case THERMAL_BLACK_HOT -> "tooltip.tacz.attachment.thermal_imaging.black_hot";
            case THERMAL_IRONBOW -> "tooltip.tacz.attachment.thermal_imaging.ironbow";
            default -> null;
        };
    }

    public boolean isThermal() {
        return this == THERMAL_WHITE_HOT || this == THERMAL_BLACK_HOT || this == THERMAL_IRONBOW;
    }
}
