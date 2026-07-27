package com.tacz.guns.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class InputExtraCheck {
    public static boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        // 不能是加载界面
        if (mc.gui.overlay() != null) {
            return false;
        }
        // 不能打开任何 GUI - 26.2 GUI重组: screen -> gui.screen()
        if (mc.gui.screen() != null) {
            return false;
        }
        // 当前窗口捕获鼠标操作
        if (!mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        // 选择了当前窗口
        return mc.isWindowActive();
    }
}
