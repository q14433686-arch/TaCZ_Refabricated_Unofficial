package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class RefitKey {
    public static final KeyMapping REFIT_KEY = new KeyMapping("key.tacz.refit.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            TaCZKeyCategory.TACZ);

    public static void onRefitPress(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && REFIT_KEY.matches(new net.minecraft.client.input.KeyEvent(event.getKey(), event.getScanCode(), event.getModifiers()))) onRefitPress();
    }

    public static void onRefitPress() {
        var mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        if (isInGame() && IGun.mainHandHoldGun(player)) {
            IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
            if (gun != null && !gun.hasAttachmentLock(player.getMainHandItem())) mc.setScreen(new GunRefitScreen());
        } else if (mc.screen instanceof GunRefitScreen screen) screen.onClose();
    }
}
