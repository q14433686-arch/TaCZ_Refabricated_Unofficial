package com.tacz.guns.client.gui;

import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.config.client.ViewTuningConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.tacz.guns.client.gui.ViewTuningScreen.label;

/** Laser-style HSV controls, but these update only the editor's RGB draft (no attachment edits or packets). */
final class TuningColorPicker {
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final int[] COLORS = {0xFFFFFF, 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x3380FF, 0xFF00FF, 0x000000};
    private static final String[] NAMES = {"white", "red", "yellow", "lime", "cyan", "blue", "magenta", "black"};
    private static final String[] CHANNELS = {"hue", "saturation", "brightness"};
    private final TuningValues.Draft draft;
    private final EditBox hex;
    private final float[] hsv = new float[3];
    private final TintSlider[] sliders = new TintSlider[3];

    TuningColorPicker(Font font, int x, int y, int width, TuningValues.Draft draft, Consumer<AbstractWidget> add,
                      BiConsumer<EditBox, Boolean> validate) {
        this.draft = draft;
        int rgb = draft.get(ViewTuningConfig.RGB);
        readRgb(rgb);
        hex = new EditBox(font, x + width - 80, y + 80, 80, 18, label("rgb"));
        hex.setMaxLength(8);
        hex.setValue(format(rgb));
        hex.setTooltip(Tooltip.create(label("rgb_hint")));
        for (int i = 0; i < COLORS.length; i++) {
            int start = x + i * width / COLORS.length, end = x + (i + 1) * width / COLORS.length;
            add.accept(new Swatch(start, y, end - start - 1, Component.translatable("color.minecraft." + NAMES[i]), COLORS[i]));
        }
        for (int i = 0; i < sliders.length; i++) {
            sliders[i] = new TintSlider(x, y + 20 * (i + 1), width, i);
            add.accept(sliders[i]);
        }
        add.accept(hex);
        hex.setResponder(text -> {
            boolean valid = HEX.matcher(text).matches();
            validate.accept(hex, valid);
            if (!valid) return;
            int n = Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16);
            // A slider already updated the draft. Don't quantize its hue back through 8-bit RGB
            // (notably when dragging hue on white, then dragging saturation).
            if (n != draft.get(ViewTuningConfig.RGB)) {
                draft.set(ViewTuningConfig.RGB, n);
                readRgb(n);
                for (TintSlider slider : sliders) slider.sync();
            }
        });
    }

    private static String format(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb); }

    private void readRgb(int rgb) {
        float h = hsv[0], s = hsv[1];
        Color.RGBtoHSB(rgb >> 16, (rgb >> 8) & 255, rgb & 255, hsv);
        if (hsv[1] == 0) hsv[0] = h;
        if (hsv[2] == 0) hsv[1] = s;
    }

    private final class Swatch extends Button {
        private final int rgb;
        Swatch(int x, int y, int width, Component name, int rgb) {
            super(x, y, width, 18, name, b -> hex.setValue(format(rgb)), DEFAULT_NARRATION);
            this.rgb = rgb;
            setTooltip(Tooltip.create(name));
        }
        @Override protected void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth();
            gui.fill(x + 1, y + 1, x + w - 1, y + 15, 0xFF000000 | rgb);
            gui.outline(x, y, w, 16, isHoveredOrFocused() ? -1 : 0xFF64747E);
            if (draft.get(ViewTuningConfig.RGB) == rgb) gui.fill(x + 2, y + 16, x + w - 2, y + 18, -1);
        }
    }

    private final class TintSlider extends AbstractSliderButton {
        private final int channel;
        TintSlider(int x, int y, int width, int channel) {
            super(x, y, width, 18, label(CHANNELS[channel]), hsv[channel]);
            this.channel = channel;
            updateMessage();
        }
        void sync() { value = hsv[channel]; updateMessage(); }
        @Override protected void applyValue() {
            hsv[channel] = (float) value;
            int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
            draft.set(ViewTuningConfig.RGB, rgb);
            hex.setValue(format(rgb));
        }
        @Override protected void updateMessage() {
            String number = channel == 0 ? Math.round(value * 360) + "°" : Math.round(value * 100) + "%";
            setMessage(label("value", label(CHANNELS[channel]), number));
        }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mx, int my, float dt) {
            super.extractWidgetRenderState(gui, mx, my, dt);
            int x = getX() + 4, y = getY() + getHeight() - 4, width = getWidth() - 8;
            for (int i = 0; i < width; i += 4) {
                float f = i / (float) (width - 1);
                int color = switch (channel) {
                    case 0 -> Color.HSBtoRGB(f, 1f, 1f);
                    case 1 -> Color.HSBtoRGB(hsv[0], f, hsv[2]);
                    default -> Color.HSBtoRGB(hsv[0], hsv[1], f);
                };
                gui.fill(x + i, y, x + Math.min(i + 4, width), y + 3, color);
            }
            int pos = x + (int) Math.round(value * (width - 1));
            gui.fill(pos - 1, y, pos + 2, y + 3, 0xFF202124);
            gui.fill(pos, y, pos + 1, y + 3, -1);
        }
    }
}
