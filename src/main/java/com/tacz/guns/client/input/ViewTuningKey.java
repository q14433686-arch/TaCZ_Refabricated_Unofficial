package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class ViewTuningKey {
    public static final KeyMapping KEY = new KeyMapping("key.tacz.view_tuning.desc", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), TaCZKeyCategory.TACZ);
    public static void register() {
        KeyMappingHelper.registerKeyMapping(KEY);
        InputEvent.Key.EVENT.register(e -> press(InputConstants.Type.KEYSYM.getOrCreate(e.getKey()), e.getAction()));
        InputEvent.MouseButton.Post.EVENT.register(e -> press(InputConstants.Type.MOUSE.getOrCreate(e.getButton()), e.getAction()));
    }
    private static void press(InputConstants.Key key, int action) {
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && !KEY.isUnbound() && KEY.matches(key) && InputExtraCheck.isInGame()) ViewTuningScreen.open(null);
    }
    private ViewTuningKey() {}
}
