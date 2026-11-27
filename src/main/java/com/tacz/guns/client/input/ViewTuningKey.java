package com.tacz.guns.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Vanilla queues the mapped mouse or keyboard click. Drain it even when the editor cannot open. */
public final class ViewTuningKey {
    public static final KeyMapping KEY = new KeyMapping("key.tacz.view_tuning.desc", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), TaCZKeyCategory.TACZ);

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY.consumeClick()) {
                if (client.player != null && InputExtraCheck.isInGame()) ViewTuningScreen.open(null);
            }
        });
    }

    private ViewTuningKey() {}
}
