package com.tacz.guns.client.gui;

import com.tacz.guns.client.gameplay.ShotEnvelope;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.client.renderer.crosshair.GunCrosshair;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.client.tuning.TuningProfile;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.client.gui.HudAssignments.Scope;
import com.tacz.guns.client.tuning.TuningShare;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Dimension;
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
import net.minecraft.util.Util;
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
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern HUNDREDTHS = Pattern.compile("[+-]?(?:[0-9]+(?:[.,][0-9]{0,2})?|[.,][0-9]{1,2})");
    private static final Pattern THOUSANDTHS = Pattern.compile("[+-]?(?:[0-9]+(?:[.,][0-9]{0,3})?|[.,][0-9]{1,3})");
    private enum Page { STYLE, GEOMETRY, COLOR, SHARE, RULES }
    private Page page = Page.STYLE;
    private String shareStatus;
    @Nullable private final Screen parent;
    private final java.util.UUID owner = TuningProfiles.account();
    private final TuningProfile working = TuningProfiles.current().copy();
    private final HudAssignments target;
    private TuningValues.Draft draft = working.base();
    private boolean saveFailed;
    private final Set<EditBox> invalid = new HashSet<>();
    private final List<Button> needsValid = new ArrayList<>();
    private boolean crosshairTab;
    private int top;
    // Deliberately independent of the paused game clock and the real player's shot state.
    private final ShotEnvelope previewShots = new ShotEnvelope();

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
        this.target = new HudAssignments(working, Minecraft.getInstance().player.getMainHandItem());
    }

    public boolean isCurrentAccount() { return owner.equals(TuningProfiles.account()); }
    @Override public void tick() { if (!isCurrentAccount()) onClose(); else super.tick(); }

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
        button(X, top + 184, W, label(crosshairTab ? "reset_hud" : "reset_model"), this::resetPage);
        button(X, top + 204, HALF, CommonComponents.GUI_CANCEL, this::onClose);
        needsValid.add(button(X + HALF + 2, top + 204, HALF, label("save"), () -> {
            if (invalid.isEmpty()) {
                stageHud();
                if (TuningProfiles.save(owner, working)) onClose();
                else saveFailed = true;
            }
        }));
    }

    private int row(int index) { return top + 62 + index * 20; }

    private Button button(int x, int y, int width, Component title, Runnable press) {
        return addRenderableWidget(Button.builder(title, b -> press.run()).bounds(x, y, width, H).build());
    }

    private void tab(boolean crosshair) {
        stageHud();
        crosshairTab = crosshair;
        loadTarget();
        shareStatus = null;
        rebuildWidgets();
    }

    private void addAxis(Axis axis, int y) {
        addNumber(y, label("axis." + key(axis)), axis.min, axis.max, draft.get(axis.option), 3, true,
                label("axis_range", axis.min, axis.max), n -> draft.set(axis.option, n / 1000d));
    }

    /** The slider and field edit the same fixed-point units. A narrow thumb cannot select every hundredth. */
    private void addNumber(int y, Component name, double min, double max, double current, int places,
                           boolean enabled, Component help, IntConsumer change) {
        int factor = places == 3 ? 1000 : places == 2 ? 100 : 1;
        var pattern = places == 3 ? THOUSANDTHS : places == 2 ? HUNDREDTHS : INTEGER;
        var format = "%." + places + "f";
        EditBox box = new EditBox(font, X + W - 47, y, 47, H, name);
        box.setMaxLength(14);
        box.setValue(String.format(Locale.ROOT, format, current));
        var slider = addRenderableWidget(new Slider(X, y, W - 50, name,
                (int) Math.round(min * factor), (int) Math.round(max * factor), (int) Math.round(current * factor),
                n -> box.setValue(String.format(Locale.ROOT, format, n / (double) factor))));
        addRenderableWidget(box);
        slider.active = box.active = enabled;
        box.setEditable(enabled);
        box.setTooltip(Tooltip.create(help)); slider.setTooltip(Tooltip.create(help));
        box.setResponder(text -> {
            double number = pattern.matcher(text).matches() ? Double.parseDouble(text.replace(',', '.')) : Double.NaN;
            boolean valid = number >= min && number <= max;
            validate(box, valid);
            if (valid) {
                int n = (int) Math.round(number * factor);
                change.accept(n);
                slider.sync(n);
            }
        });
    }

    private void initCrosshair() {
        for (Page p : Page.values()) {
            int count = Page.values().length;
            Button b = button(X + W * p.ordinal() / count, top + 40, W / count - 1, label("page." + key(p)), () -> {
                page = p;
                shareStatus = null;
                rebuildWidgets();
            });
            if (p == page) b.active = false; else needsValid.add(b);
        }
        Mode mode = draft.get(ViewTuningConfig.MODE);
        switch (page) {
            case STYLE -> {
                needsValid.add(button(X, row(0), W, label("mode." + key(mode)), () -> {
                    draft.set(ViewTuningConfig.MODE, next(mode));
                    rebuildWidgets();
                }));
                if (mode == Mode.PRESET) {
                    button(X, row(1), 20, Component.literal("<"), () -> cyclePreset(-1)).setTooltip(Tooltip.create(label("previous")));
                    button(X + W - 20, row(1), 20, Component.literal(">"), () -> cyclePreset(1)).setTooltip(Tooltip.create(label("next")));
                } else if (mode == Mode.DESIGNER) {
                    Shape shape = draft.get(ViewTuningConfig.SHAPE);
                    needsValid.add(button(X, row(1), W, label("shape." + key(shape)), () -> {
                        draft.set(ViewTuningConfig.SHAPE, next(shape));
                        rebuildWidgets();
                    }));
                }
                boolean dynamic = draft.get(ViewTuningConfig.DYNAMIC);
                var motion = button(X, row(2), W, label(dynamic ? "motion.dynamic" : "motion.static"), () -> {
                    previewShots.clear();
                    draft.set(ViewTuningConfig.DYNAMIC, !dynamic);
                    rebuildWidgets();
                });
                needsValid.add(motion); motion.setTooltip(Tooltip.create(label("spread_hint")));
                addMeasure(Measure.SPREAD_SCALE, 3, dynamic);
                if (dynamic) {
                    addDimension(Dimension.KICK, 4, true);
                    addMeasure(Measure.RECOVERY, 5, true);
                    int r = previewRadius();
                    if (r > 20) {
                        int w = Math.min(2 * r, 108);
                        button(previewX(r) - w / 2, height / 2 + r + 2, w, label("preview_shot"),
                                () -> previewShots.fire(Util.getMillis(), draft.get(Measure.RECOVERY.option)));
                    }
                }
            }
            case GEOMETRY -> {
                boolean lines = mode == Mode.DESIGNER;
                boolean ring = lines && draft.get(ViewTuningConfig.SHAPE) == Shape.RING;
                addDimension(ring ? Dimension.RADIUS : lines ? Dimension.LENGTH : Dimension.SIZE, 0, true);
                addMeasure(Measure.ASPECT, 1, !lines || draft.get(ViewTuningConfig.SHAPE) != Shape.SIDES);
                if (lines) {
                    if (!ring) addDimension(Dimension.GAP, 2, true);
                    addDimension(Dimension.THICKNESS, 3, true);
                    toggle(X, ViewTuningConfig.DOT, "dot");
                    toggle(X + HALF + 2, ViewTuningConfig.OUTLINE, "outline");
                    addDimension(Dimension.DOT_SIZE, 5, draft.get(ViewTuningConfig.DOT));
                } else {
                    TextureCenter center = draft.get(ViewTuningConfig.TEXTURE_CENTER);
                    needsValid.add(button(X, row(2), W, label("center." + key(center)), () -> {
                        draft.set(ViewTuningConfig.TEXTURE_CENTER, next(center));
                        rebuildWidgets();
                    }));
                    addDimension(Dimension.DOT_SIZE, 3, center == TextureCenter.DOT);
                }
            }
            case COLOR -> {
                new TuningColorPicker(font, X, row(0), W, draft, this::addRenderableWidget, this::validate);
                addMeasure(Measure.OPACITY, 5, true);
            }
            case SHARE -> shareButtons(row(0));
            case RULES -> initRules();
        }
    }

    // An inherited style is read-only until the first edit. Capture the entire resolved HUD on
    // edit; merely browsing a type/gun must not pin it or copy its position. All of this is local
    // to this screen until Save, including deleting an existing rule.
    private void stageHud() {
        if (crosshairTab && target.scope != Scope.DEFAULT && draft.changed())
            working.setHud(target.type(), target.gun(), draft);
    }

    private void loadTarget() {
        draft = !crosshairTab || target.scope == Scope.DEFAULT ? working.base()
                : new TuningValues.Draft(working.hud(target.type(), target.gun()));
        previewShots.clear();
    }

    private void select(Scope scope, @Nullable String key) {
        stageHud();
        target.scope = scope;
        if (key != null) target.select(key);
        loadTarget(); shareStatus = null;
        rebuildWidgets();
    }

    private void initRules() {
        for (Scope scope : Scope.values()) {
            var b = button(X + W * scope.ordinal() / 3, row(0), W / 3 - 1, label("scope." + key(scope)), () -> select(scope, null));
            b.active = scope != target.scope && target.available(scope);
        }
        if (target.scope == Scope.DEFAULT) return;
        button(X, row(2), 22, Component.literal("<"), () -> select(target.scope, target.step(-1)));
        button(X + 24, row(2), W - 48, label("held"), () -> select(target.scope, target.held())).active = target.held() != null;
        button(X + W - 22, row(2), 22, Component.literal(">"), () -> select(target.scope, target.step(1)));
        var search = addRenderableWidget(new EditBox(font, X, row(3), W - 47, H, label("search_hint")));
        search.setMaxLength(256); search.setHint(label("search_hint"));
        button(X + W - 45, row(3), 45, label("find"), () -> {
            var match = target.find(search.getValue());
            if (match != null) select(target.scope, match); else shareStatus = "not_found";
        });
        boolean override = draft.changed() || working.overrides(target.type(), target.gun());
        button(X, row(4), W, label(override ? "inherit" : "customize"), () -> {
            if (override) working.inherit(target.type(), target.gun());
            else working.setHud(target.type(), target.gun(), draft);
            loadTarget(); shareStatus = null; rebuildWidgets();
        });
    }

    private static String key(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }
    private static <E extends Enum<E>> E next(E e) {
        E[] all = e.getDeclaringClass().getEnumConstants();
        return all[(e.ordinal() + 1) % all.length];
    }

    private void addMeasure(Measure m, int index, boolean enabled) {
        addNumber(row(index), label(key(m)), m.min, m.max, draft.get(m.option), 0, enabled,
                label("int_range", m.min, m.max), n -> draft.set(m.option, n));
    }

    private void addDimension(Dimension d, int index, boolean enabled) {
        var help = Component.empty().append(label("geometry_range", d.min, d.max));
        if (d == Dimension.DOT_SIZE) help.append(" ").append(label("dot_size_hint"));
        addNumber(row(index), label(key(d)), d.min, d.max, draft.get(d.option), 2, enabled,
                help, n -> draft.set(d.option, n / 100d));
    }

    private int previewRadius() { return Math.min(80, (width - X - W - 18) / 2); }
    private int previewX(int r) { return Math.max(width / 2, X + W + 8 + r); }

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
        needsValid.add(button(x, row(4), HALF, toggleLabel(key, option), () -> {
            draft.set(option, !draft.get(option));
            rebuildWidgets();
        }));
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
            for (Dimension d : Dimension.values()) draft.reset(d.option);
            previewShots.clear();
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
        if (!isCurrentAccount()) return;
        Component heading = !invalid.isEmpty() ? label("invalid") : saveFailed ? label("save_failed") : !crosshairTab ? title
                : target.scope == Scope.DEFAULT ? label("editing_default") : label("value", label("scope." + key(target.scope)), target.name(target.key()));
        gui.text(font, font.plainSubstrByWidth(heading.getString(), W - 2), X + 1, top + 5, invalid.isEmpty() && !saveFailed ? -1 : 0xFFFF9999, false);
        if (crosshairTab) {
            Mode mode = draft.get(ViewTuningConfig.MODE);
            switch (page) {
                case STYLE -> {
                    if (mode == Mode.PRESET) gui.centeredText(font, draft.get(RenderConfig.CROSSHAIR_TYPE).name(), X + W / 2, row(1) + 5, -1);
                    if (!draft.get(ViewTuningConfig.DYNAMIC)) hint(gui, label(mode == Mode.PNG ? "png_hint" : "spread_hint"), row(4));
                }
                case GEOMETRY -> {
                    if (mode != Mode.DESIGNER) hint(gui, label("center_hint"), row(4));
                    else if (draft.get(ViewTuningConfig.SHAPE) == Shape.RING) hint(gui, label("ring_hint"), row(2) + 4);
                }
                case COLOR -> {
                    gui.text(font, label("rgb"), X + 2, row(4) + 5, -1, false);
                    gui.fill(X + W - 96, row(4) + 3, X + W - 84, row(4) + 15, 0xFF000000 | draft.get(ViewTuningConfig.RGB));
                }
                case RULES -> {
                    if (target.scope == Scope.DEFAULT) hint(gui, label("rule_hint"), row(2));
                    else {
                        gui.text(font, font.plainSubstrByWidth(target.name(target.key()).getString(), W - 4), X + 2, row(1) + 5, -1, false);
                        if (shareStatus != null) status(gui, row(5) + 4);
                        else gui.text(font, font.plainSubstrByWidth(target.key(), W - 4), X + 2, row(5) + 4, 0xFFE0E7E9, false);
                    }
                }
                case SHARE -> {
                    hint(gui, label("share_hint"), row(2));
                    status(gui, row(1) + 4);
                }
            }
            int r = previewRadius();
            int cx = previewX(r), cy = height / 2;
            if (r > 4) {
                gui.fill(cx - r, cy - r, cx, cy + r, 0xFF35414B);
                gui.fill(cx, cy - r, cx + r, cy + r, 0xFF8D999E);
                gui.centeredText(font, label("preview"), cx, cy - r - 12, -1);
                GunCrosshair.draw(gui, cx, cy, draft, r - 3, previewShots.sample(Util.getMillis()));
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
        private final IntConsumer change;

        Slider(int x, int y, int width, Component name, int min, int max, int current, IntConsumer change) {
            super(x, y, width, H, name, 0);
            this.name = name;
            this.min = min;
            this.max = max;
            this.change = change;
            sync(current);
        }

        private void sync(int n) { value = (n - min) / (double) (max - min); updateMessage(); }
        private int number() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void applyValue() { change.accept(number()); }
        @Override protected void updateMessage() { setMessage(name); }
    }
}
