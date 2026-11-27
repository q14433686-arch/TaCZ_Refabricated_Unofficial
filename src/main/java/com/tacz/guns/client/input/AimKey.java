package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class AimKey {
    public static final KeyMapping AIM_KEY = new KeyMapping("key.tacz.aim.desc",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            TaCZKeyCategory.TACZ);

    public static void onAimPress(InputEvent.MouseButton.Post event) {
        var input = new MouseButtonEvent(0, 0,
                new MouseButtonInfo(event.getButton(), event.getModifiers()));
        if (event.getAction() == GLFW.GLFW_PRESS && AIM_KEY.matchesMouse(input)) onAimControllerPress(true);
    }

    public static void onAimKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && AIM_KEY.matches(new KeyEvent(
                event.getKey(), event.getScanCode(), event.getModifiers()))) onAimControllerPress(true);
    }

    // A release from either device must not cancel the other's hold. Re-poll both in hold mode.
    private static boolean controllerAimDown;
    public static void setControllerAimDown(boolean down) { controllerAimDown = down; }

    public static void onAimHoldingPreInput(Minecraft client) {
        if (!KeyConfig.HOLD_TO_AIM.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean press = AIM_KEY.isDown() || controllerAimDown;
        if (InputExtraCheck.isInGame()) {
            LocalPlayer player = mc.player;
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!(player instanceof IClientPlayerGunOperator operator)) {
                return;
            }
            if (operator.isAim() && press) {
                return;
            }
            if (!operator.isAim()) {
                if (!press) {
                    return;
                }
            }
            if (IGun.mainHandHoldGun(player)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(press);
            }
        }
    }

    public static boolean onAimControllerPress(boolean isPress) {
        if (!isInGame()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return false;
        }
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return false;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return false;
        }
        boolean action = true;
        if (!KeyConfig.HOLD_TO_AIM.get()) {
            action = !operator.isAim();
        }
        if (isPress) {
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(action);
            return true;
        }
        if (KeyConfig.HOLD_TO_AIM.get()) {
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(AIM_KEY.isDown() || controllerAimDown);
            return true;
        }
        return false;
    }

    public static void cancelAim(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return;
        }
        if (operator.isAim() && (!isInGame() || player.isSpectator())) {
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        }
    }
}
