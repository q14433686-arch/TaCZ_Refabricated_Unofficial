package com.tacz.guns.client.tuning;

import com.tacz.guns.client.gui.ViewTuningScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.HashMap;
import java.util.Map;

/** ConfigValue supplies the typed key and factory default, not mutable global player state. */
public interface TuningValues {
    TuningValues DEFAULTS = ConfigValue::getDefault;
    <T> T get(ConfigValue<T> option);

    static TuningValues visible() {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ViewTuningScreen editor && editor.isCurrentAccount()) return editor.draft();
        return mc.player == null ? TuningProfiles.current().base() : TuningProfiles.forStack(mc.player.getMainHandItem());
    }

    final class Draft implements TuningValues {
        private final TuningValues fallback;
        private final Map<ConfigValue<?>, Object> edits = new HashMap<>();

        public Draft() { this(DEFAULTS); }
        public Draft(TuningValues fallback) { this.fallback = fallback; }

        @Override
        @SuppressWarnings("unchecked") // Only set<T> pairs a key with a value.
        public <T> T get(ConfigValue<T> option) {
            Object value = edits.get(option);
            return value == null ? fallback.get(option) : (T) value;
        }

        public <T> void set(ConfigValue<T> option, T value) { edits.put(option, value); }
        public <T> void reset(ConfigValue<T> option) { set(option, option.getDefault()); }
        void merge(Draft validated) { edits.putAll(validated.edits); }
        public boolean changed() { return !edits.isEmpty(); }
    }
}
