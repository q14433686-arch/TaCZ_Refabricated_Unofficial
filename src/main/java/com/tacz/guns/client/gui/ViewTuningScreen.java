package com.tacz.guns.client.gui;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.client.renderer.crosshair.GunCrosshair;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.client.tuning.TuningShare;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.config.client.ViewTuningConfig.Shape;
import com.tacz.guns.config.client.ViewTuningConfig.TextureCenter;
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
    private static final int X = 8, W = 168, HALF = (W - 2) / 2, H = 18;
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:[.,][0-9]{0,3})?|[.,][0-9]{1,3})");
    private enum Page { STYLE, GEOMETRY, COLOR, SHARE }
    private Page page = Page.STYLE;
    private String shareStatus;
    @Nullable private final Screen parent;
    private final TuningValues.Draft draft = new TuningValues.Draft();
    private final Set<EditBox> invalid = new HashSet<>();
    private final List<Button> needsValid = new ArrayList<>();
    private boolean crosshairTab;
    private int top;

    static Component label(String key, Object... args) {
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
        if (crosshairTab) {
            initCrosshair();
        } else {
            for (Axis axis : Axis.values()) addAxis(axis, top + 41 + 20 * axis.ordinal());
            shareButtons(top + 142);
        }
        button(X, top + 184, W, label("reset"), this::resetPage);
        button(X, top + 204, HALF, CommonComponents.GUI_CANCEL, this::onClose);
        needsValid.add(button(X + HALF + 2, top + 204, HALF, label("save"), () -> {
            if (invalid.isEmpty()) {
                draft.save();
                onClose();
            }
        }));
    }

    private int row(int index) { return top + 62 + index * 20; }

    private Button button(int x, int y, int width, Component title, Runnable press) {
        return addRenderableWidget(Button.builder(title, b -> press.run()).bounds(x, y, width, H).build());
    }

    private void tab(boolean crosshair) {
        crosshairTab = crosshair;
        shareStatus = null;
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
        for (Page p : Page.values()) {
            Button b = button(X + W / 4 * p.ordinal(), top + 40, W / 4 - 1, label("page." + key(p)), () -> {
                page = p;
                shareStatus = null;
                rebuildWidgets();
            });
            if (p == page) b.active = false; else needsValid.add(b);
        }
        Mode mode = draft.get(ViewTuningConfig.MODE);
        switch (page) {
            case STYLE -> {
                button(X, row(0), W, label("mode." + key(mode)), () -> {
                    draft.set(ViewTuningConfig.MODE, next(mode));
                    rebuildWidgets();
                });
                if (mode == Mode.PRESET) {
                    button(X, row(1), 20, Component.literal("<"), () -> cyclePreset(-1)).setTooltip(Tooltip.create(label("previous")));
                    button(X + W - 20, row(1), 20, Component.literal(">"), () -> cyclePreset(1)).setTooltip(Tooltip.create(label("next")));
                } else if (mode == Mode.DESIGNER) {
                    Shape shape = draft.get(ViewTuningConfig.SHAPE);
                    button(X, row(1), W, label("shape." + key(shape)), () -> {
                        draft.set(ViewTuningConfig.SHAPE, next(shape));
                        rebuildWidgets();
                    });
                }
                boolean dynamic = draft.get(ViewTuningConfig.DYNAMIC);
                button(X, row(2), W, label(dynamic ? "motion.dynamic" : "motion.static"), () -> {
                    draft.set(ViewTuningConfig.DYNAMIC, !dynamic);
                    rebuildWidgets();
                }).setTooltip(Tooltip.create(label("spread_hint")));
                addMeasure(Measure.SPREAD_SCALE, 3).active = dynamic;
            }
            case GEOMETRY -> {
                boolean lines = mode == Mode.DESIGNER;
                addMeasure(lines ? Measure.LENGTH : Measure.SIZE, 0);
                addMeasure(Measure.ASPECT, 1).active = !lines || draft.get(ViewTuningConfig.SHAPE) != Shape.SIDES;
                if (lines) {
                    addMeasure(Measure.GAP, 2);
                    addMeasure(Measure.THICKNESS, 3);
                    toggle(X, ViewTuningConfig.DOT, "dot");
                    toggle(X + HALF + 2, ViewTuningConfig.OUTLINE, "outline");
                    addMeasure(Measure.DOT_SIZE, 5).active = draft.get(ViewTuningConfig.DOT);
                } else {
                    TextureCenter center = draft.get(ViewTuningConfig.TEXTURE_CENTER);
                    button(X, row(2), W, label("center." + key(center)), () -> {
                        draft.set(ViewTuningConfig.TEXTURE_CENTER, next(center));
                        rebuildWidgets();
                    });
                    addMeasure(Measure.DOT_SIZE, 3).active = center == TextureCenter.DOT;
                }
            }
            case COLOR -> {
                new TuningColorPicker(font, X, row(0), W, draft, this::addRenderableWidget, this::validate);
                addMeasure(Measure.OPACITY, 5);
            }
            case SHARE -> shareButtons(row(0));
        }
    }

    private static String key(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }
    private static <E extends Enum<E>> E next(E e) {
        E[] all = e.getDeclaringClass().getEnumConstants();
        return all[(e.ordinal() + 1) % all.length];
    }

    private Slider addMeasure(Measure measure, int index) {
        Slider slider = addRenderableWidget(new Slider(X, row(index), W, label(key(measure)),
                measure.min, measure.max, draft.get(measure.option), true, n -> draft.set(measure.option, n)));
        if (measure == Measure.DOT_SIZE) slider.setTooltip(Tooltip.create(label("dot_size_hint")));
        return slider;
    }

    private void shareButtons(int y) {
        var group = crosshairTab ? TuningShare.CROSSHAIR : TuningShare.POSITION;
        needsValid.add(button(X, y, HALF, label("copy"), () -> {
            minecraft.keyboardHandler.setClipboard(group.encode(draft));
            shareStatus = "copied";
        }));
        needsValid.add(button(X + HALF + 2, y, HALF, label("paste"), () -> {
            if (group.decode(minecraft.keyboardHandler.getClipboard(), draft)) {
                shareStatus = "imported";
                rebuildWidgets();
            } else shareStatus = "bad_code";
        }));
    }

    private void toggle(int x, BooleanValue option, String key) {
        button(x, row(4), HALF, toggleLabel(key, option), () -> {
            draft.set(option, !draft.get(option));
            rebuildWidgets();
        });
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
        shareStatus = null;
        if (crosshairTab) {
            draft.reset(ViewTuningConfig.DYNAMIC);
            draft.reset(ViewTuningConfig.SHAPE);
            draft.reset(ViewTuningConfig.TEXTURE_CENTER);
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
            switch (page) {
                case STYLE -> {
                    if (mode == Mode.PRESET) gui.centeredText(font, draft.get(RenderConfig.CROSSHAIR_TYPE).name(), X + W / 2, row(1) + 5, -1);
                    hint(gui, label(mode == Mode.PNG ? "png_hint" : "spread_hint"), row(4));
                }
                case GEOMETRY -> {
                    if (mode != Mode.DESIGNER) hint(gui, label("center_hint"), row(4));
                }
                case COLOR -> {
                    gui.text(font, label("rgb"), X + 2, row(4) + 5, -1, false);
                    gui.fill(X + W - 96, row(4) + 3, X + W - 84, row(4) + 15, 0xFF000000 | draft.get(ViewTuningConfig.RGB));
                }
                case SHARE -> {
                    hint(gui, label("share_hint"), row(2));
                    status(gui, row(1) + 4);
                }
            }
            int r = Math.min(80, (width - X - W - 18) / 2);
            int cx = Math.max(width / 2, X + W + 8 + r), cy = height / 2;
            if (r > 4) {
                gui.fill(cx - r, cy - r, cx, cy + r, 0xFF35414B);
                gui.fill(cx, cy - r, cx + r, cy + r, 0xFF8D999E);
                gui.centeredText(font, label("preview"), cx, cy - r - 12, -1);
                GunCrosshair.draw(gui, cx, cy, draft, r - 3);
            }
        } else {
            hint(gui, label("model_hint"), top + 102);
            status(gui, top + 163);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void status(GuiGraphicsExtractor gui, int y) {
        if (shareStatus != null) gui.text(font, label(shareStatus), X + 2, y,
                shareStatus.equals("bad_code") ? 0xFFFF9999 : 0xFF9EEDB5, false);
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
