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
        if (event.getAction() == GLFW.GLFW_PRESS && REFIT_KEY.matches(InputConstants.Type.KEYSYM.getOrCreate(event.getKey()))) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            if (isInGame()) {
                if (IGun.mainHandHoldGun(player) && Minecraft.getInstance().gui.screen() == null) {
                    IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
                    if (iGun != null && iGun.hasAttachmentLock(player.getMainHandItem())) {
                        return;
                    }
                    Minecraft.getInstance().gui.setScreen(new GunRefitScreen());
                }
            } else if (Minecraft.getInstance().gui.screen() instanceof GunRefitScreen refitScreen) {
                refitScreen.onClose();
            }
        }
    }
}
