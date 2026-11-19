package com.tacz.guns.config.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Player-local presentation only. The existing render.CrosshairType remains the preset selector. */
public final class ViewTuningConfig {
    public enum Mode { PRESET, DESIGNER, PNG }
    public enum Shape { CROSS, T, SIDES, BOX }
    public enum TextureCenter { ORIGINAL, CLEAR, DOT }

    public enum Axis {
        X(-1, 1), Y(-1, 1), Z(-5, 1);

        public final int min, max;
        public ForgeConfigSpec.DoubleValue option;

        Axis(int min, int max) { this.min = min; this.max = max; }
    }

    public enum Measure {
        SIZE("TextureSize", 16, 4, 64), LENGTH("ArmLength", 4, 0, 24),
        GAP("Gap", 2, 0, 16), THICKNESS("Thickness", 1, 1, 8), OPACITY("Opacity", 230, 0, 255),
        ASPECT("VerticalPercent", 100, 25, 200), DOT_SIZE("DotSize", 0, 0, 12),
        SPREAD_SCALE("SpreadPercent", 100, 25, 200);

        private final String key;
        private final int initial;
        public final int min, max;
        public ForgeConfigSpec.IntValue option;

        Measure(String key, int initial, int min, int max) {
            this.key = key;
            this.initial = initial;
            this.min = min;
            this.max = max;
        }
    }

    public static ForgeConfigSpec.EnumValue<Mode> MODE;
    public static ForgeConfigSpec.EnumValue<Shape> SHAPE;
    public static ForgeConfigSpec.EnumValue<TextureCenter> TEXTURE_CENTER;
    public static ForgeConfigSpec.IntValue RGB;
    public static ForgeConfigSpec.BooleanValue DOT, OUTLINE, DYNAMIC;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.comment("First-person hip position in view units: +X right, +Y up, +Z closer. Zero keeps the pack pose.").push("viewmodel");
        for (Axis axis : Axis.values()) {
            axis.option = builder.defineInRange("Offset" + axis.name(), 0d, axis.min, axis.max);
        }
        builder.pop();
        builder.comment("Gun HUD crosshair only; sizes are in GUI pixels, not framebuffer pixels.").push("crosshair");
        MODE = builder.comment("PNG uses assets/tacz/textures/crosshair/custom.png from your resource pack.")
                .defineEnum("Mode", Mode.PRESET);
        SHAPE = builder.defineEnum("LineShape", Shape.CROSS);
        TEXTURE_CENTER = builder.comment("CLEAR or DOT removes the central quarter of the image (width and height).")
                .defineEnum("TextureCenter", TextureCenter.ORIGINAL);
        DYNAMIC = builder.comment("Expand the HUD using client-visible gun spread. Does not change projectile behavior.")
                .define("FollowGunSpread", false);
        for (Measure measure : Measure.values()) {
            measure.option = builder.defineInRange(measure.key, measure.initial, measure.min, measure.max);
        }
        RGB = builder.comment("24-bit RGB tint; white leaves texture colors unchanged.").defineInRange("ColorRGB", 0xFFFFFF, 0, 0xFFFFFF);
        DOT = builder.define("CenterDot", true);
        OUTLINE = builder.define("Outline", false);
        builder.pop();
    }

    private ViewTuningConfig() { }
}
