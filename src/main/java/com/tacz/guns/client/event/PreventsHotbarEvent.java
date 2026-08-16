package com.tacz.guns.client.event;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class PreventsHotbarEvent {
    public static void onRenderHotbarEvent(AtomicBoolean cancelled) {
        // 在役路径：GuiMixin 于 Hud#extractRenderState HEAD 调用；返回 true 会取消
        // 本帧整个原版 HUD 提取。两个全屏 TACZ 容器自行绘制界面，因此这里用于
        // 避免背后的快捷栏/HUD 透出，并非尚未接线的测试桩。
        Screen screen = Minecraft.getInstance().gui.screen();
        // 枪械合成台界面关闭背景
        if (screen instanceof GunSmithTableScreen) {
            cancelled.set(true);
            return;
        }
        // 枪械改装界面关闭背景
        if (screen instanceof GunRefitScreen) {
            cancelled.set(true);
        }
    }
}
