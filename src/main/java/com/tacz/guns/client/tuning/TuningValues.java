package com.tacz.guns.client.tuning;

import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.config.ConfigPersist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.HashMap;
import java.util.Map;

/** A renderer sees the editor's draft only while that very screen is on top. No global preview to clear on disconnect. */
public interface TuningValues {
    TuningValues SAVED = ConfigValue::get;
    <T> T get(ConfigValue<T> option);

    static TuningValues visible() {
        return Minecraft.getInstance().gui.screen() instanceof ViewTuningScreen editor ? editor.draft() : SAVED;
    }

    final class Draft implements TuningValues {
        private record Edit<T>(ConfigValue<T> option, T value) {
            void apply() { option.set(value); }
        }

        private final Map<ConfigValue<?>, Edit<?>> edits = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked") // set() always stores Edit<T> under its own ConfigValue<T>.
        public <T> T get(ConfigValue<T> option) {
            Edit<?> edit = edits.get(option);
            return edit == null ? option.get() : (T) edit.value();
        }

        public <T> void set(ConfigValue<T> option, T value) {
            edits.put(option, new Edit<>(option, value));
        }

        public <T> void reset(ConfigValue<T> option) { set(option, option.getDefault()); }

        public void save() {
            if (edits.isEmpty()) return;
            edits.values().forEach(Edit::apply);
            ConfigPersist.saveClient();
            edits.clear();
        }
    }
}
