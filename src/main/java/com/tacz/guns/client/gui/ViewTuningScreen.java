package com.tacz.guns.client.gui;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.client.renderer.crosshair.GunCrosshair;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** Native, optional-mod-independent live editor. Keep the singleplayer pause; the hand pass still extracts each frame. */
public final class ViewTuningScreen extends Screen {
    private static final int X = 8, W = 154, HALF = (W - 2) / 2, H = 18;
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:[.,][0-9]{0,3})?|[.,][0-9]{1,3})");
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
    @Nullable private final Screen parent;
    private final TuningValues.Draft draft = new TuningValues.Draft();
    private final Set<EditBox> invalid = new HashSet<>();
    private final List<Button> needsValid = new ArrayList<>();
    private boolean crosshairTab;
    private int top;

    private static Component label(String key, Object... args) {
        return Component.translatable("gui.tacz.view_tuning." + key, args);
    }

    public static void registerPauseButton() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && mc.player != null) {
                Component title = label("title");
                Screens.getWidgets(screen).add(Button.builder(title, b -> open(pause))
                        .bounds(4, height - 24, mc.font.width(title) + 16, 20).build());
            }
        });
    }

    public static void open(@Nullable Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.setScreen(new ViewTuningScreen(parent));
    }

    private ViewTuningScreen(@Nullable Screen parent) {
        super(label("title"));
        this.parent = parent;
    }

    public TuningValues draft() { return draft; }
    public boolean isCrosshairTab() { return crosshairTab; }

    @Override
    protected void init() {
        top = Math.max(2, (height - 226) / 2);
        invalid.clear();
        needsValid.clear();
        Button model = button(X, top + 18, HALF, label("model"), () -> tab(false));
        Button hud = button(X + HALF + 2, top + 18, HALF, label("crosshair"), () -> tab(true));
        (crosshairTab ? hud : model).active = false;
        needsValid.add(crosshairTab ? model : hud);
        if (crosshairTab) initCrosshair();
        else for (Axis axis : Axis.values()) addAxis(axis, row(axis.ordinal()));
        button(X, top + 184, W, label("reset"), this::resetPage);
        button(X, top + 204, HALF, CommonComponents.GUI_CANCEL, this::onClose);
        needsValid.add(button(X + HALF + 2, top + 204, HALF, label("save"), () -> {
            if (invalid.isEmpty()) {
                draft.save();
                onClose();
            }
        }));
    }

    private int row(int index) { return top + 41 + index * 20; }

    private Button button(int x, int y, int width, Component title, Runnable press) {
        return addRenderableWidget(Button.builder(title, b -> press.run()).bounds(x, y, width, H).build());
    }

    private void tab(boolean crosshair) {
        crosshairTab = crosshair;
        rebuildWidgets();
    }

    private void addAxis(Axis axis, int y) {
        double value = draft.get(axis.option);
        int units = (int) Math.round(value * 1000);
        Component name = label("axis." + axis.name().toLowerCase(Locale.ROOT));
        EditBox box = new EditBox(font, X + W - 47, y, 47, H, name);
        box.setMaxLength(14);
        box.setValue(formatAxis(units));
        Tooltip range = Tooltip.create(label("axis_range", axis.min, axis.max));
        box.setTooltip(range);
        Slider slider = addRenderableWidget(new Slider(X, y, W - 50, name, axis.min * 1000, axis.max * 1000,
                units, false, n -> box.setValue(formatAxis(n))));
        slider.setTooltip(range);
        addRenderableWidget(box);
        box.setResponder(text -> {
            double number = DECIMAL.matcher(text).matches() ? Double.parseDouble(text.replace(',', '.')) : Double.NaN;
            boolean valid = number >= axis.min && number <= axis.max;
            validate(box, valid);
            if (valid) {
                int n = (int) Math.round(number * 1000);
                draft.set(axis.option, n / 1000d);
                slider.sync(n); // Move the thumb without firing the text responder again.
            }
        });
    }

    private static String formatAxis(int units) { return String.format(Locale.ROOT, "%.3f", units / 1000d); }

    private void initCrosshair() {
        Mode mode = draft.get(ViewTuningConfig.MODE);
        needsValid.add(button(X, row(0), W, label("mode." + mode.name().toLowerCase(Locale.ROOT)), () -> {
            Mode[] modes = Mode.values();
            draft.set(ViewTuningConfig.MODE, modes[(mode.ordinal() + 1) % modes.length]);
            rebuildWidgets();
        }));
        if (mode == Mode.DESIGNER) {
            addMeasure(Measure.LENGTH, 1);
            addMeasure(Measure.GAP, 2);
            addMeasure(Measure.THICKNESS, 3);
            toggle(X, ViewTuningConfig.DOT, "dot");
            toggle(X + HALF + 2, ViewTuningConfig.OUTLINE, "outline");
        } else {
            if (mode == Mode.PRESET) {
                button(X, row(1), 20, Component.literal("<"), () -> cyclePreset(-1)).setTooltip(Tooltip.create(label("previous")));
                button(X + W - 20, row(1), 20, Component.literal(">"), () -> cyclePreset(1)).setTooltip(Tooltip.create(label("next")));
            }
            addMeasure(Measure.SIZE, 2);
        }
        EditBox rgb = addRenderableWidget(new EditBox(font, X + W - 80, row(5), 80, H, label("rgb")));
        rgb.setMaxLength(8);
        rgb.setValue(String.format(Locale.ROOT, "#%06X", draft.get(ViewTuningConfig.RGB)));
        rgb.setTooltip(Tooltip.create(label("rgb_hint")));
        rgb.setResponder(text -> {
            boolean valid = HEX.matcher(text).matches();
            validate(rgb, valid);
            if (valid) draft.set(ViewTuningConfig.RGB, Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16));
        });
        addMeasure(Measure.OPACITY, 6);
    }

    private void addMeasure(Measure measure, int index) {
        addRenderableWidget(new Slider(X, row(index), W, label(measure.name().toLowerCase(Locale.ROOT)),
                measure.min, measure.max, draft.get(measure.option), true, n -> draft.set(measure.option, n)));
    }

    private void toggle(int x, BooleanValue option, String key) {
        addRenderableWidget(Button.builder(toggleLabel(key, option), b -> {
            draft.set(option, !draft.get(option));
            b.setMessage(toggleLabel(key, option));
        }).bounds(x, row(4), HALF, H).build());
    }

    private Component toggleLabel(String key, BooleanValue option) {
        return label("value", label(key), Component.translatable(draft.get(option) ? "options.on" : "options.off"));
    }

    private void cyclePreset(int delta) {
        CrosshairType[] presets = CrosshairType.values();
        draft.set(RenderConfig.CROSSHAIR_TYPE, presets[Math.floorMod(draft.get(RenderConfig.CROSSHAIR_TYPE).ordinal() + delta, presets.length)]);
    }

    private void validate(EditBox field, boolean valid) {
        if (valid) invalid.remove(field); else invalid.add(field);
        field.setTextColor(valid ? 0xFFE0E0E0 : 0xFFFF7777);
        needsValid.forEach(button -> button.active = invalid.isEmpty());
    }

    private void resetPage() {
        if (crosshairTab) {
            draft.reset(ViewTuningConfig.MODE);
            draft.reset(RenderConfig.CROSSHAIR_TYPE);
            for (Measure measure : Measure.values()) draft.reset(measure.option);
            draft.reset(ViewTuningConfig.RGB);
            draft.reset(ViewTuningConfig.DOT);
            draft.reset(ViewTuningConfig.OUTLINE);
        } else {
            for (Axis axis : Axis.values()) draft.reset(axis.option);
        }
        rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // No panorama, full-screen tint or blur: the world and actual held model are the preview.
        gui.fill(X - 4, top - 2, X + W + 4, top + 226, 0xDD172028);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        gui.text(font, invalid.isEmpty() ? title : label("invalid"), X + 1, top + 5, invalid.isEmpty() ? -1 : 0xFFFF9999, false);
        if (crosshairTab) {
            Mode mode = draft.get(ViewTuningConfig.MODE);
            if (mode == Mode.PRESET) {
                gui.centeredText(font, draft.get(RenderConfig.CROSSHAIR_TYPE).name(), X + W / 2, row(1) + 5, -1);
            } else if (mode == Mode.PNG) {
                hint(gui, label("png_hint"), row(3));
            }
            gui.text(font, label("rgb"), X + 2, row(5) + 5, -1, false);
            // Even at minimum GUI width, leave enough room for the largest possible shape.
            int cx = Math.max(width / 2, X + W + 53), cy = height / 2;
            gui.fill(cx - 47, cy - 47, cx, cy + 47, 0xFF35414B);
            gui.fill(cx, cy - 47, cx + 47, cy + 47, 0xFF8D999E);
            gui.centeredText(font, label("preview"), cx, cy - 59, -1);
            GunCrosshair.draw(gui, cx, cy, draft);
        } else {
            hint(gui, label("model_hint"), row(3) + 4);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void hint(GuiGraphicsExtractor gui, Component message, int y) {
        for (var line : font.split(message, W - 4)) {
            gui.text(font, line, X + 2, y, 0xFFE0E7E9, false);
            y += font.lineHeight;
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private static final class Slider extends AbstractSliderButton {
        private final Component name;
        private final int min, max;
        private final boolean displayNumber;
        private final IntConsumer change;

        Slider(int x, int y, int width, Component name, int min, int max, int current, boolean displayNumber, IntConsumer change) {
            super(x, y, width, H, name, 0);
            this.name = name;
            this.min = min;
            this.max = max;
            this.displayNumber = displayNumber;
            this.change = change;
            sync(current);
        }

        private void sync(int n) { value = (n - min) / (double) (max - min); updateMessage(); }
        private int number() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void applyValue() { change.accept(number()); }
        @Override protected void updateMessage() { setMessage(displayNumber ? label("value", name, number()) : name); }
    }
}
