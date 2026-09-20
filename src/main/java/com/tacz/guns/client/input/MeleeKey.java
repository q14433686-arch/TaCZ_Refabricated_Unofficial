package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class MeleeKey {
    public static final KeyMapping MELEE_KEY = new KeyMapping("key.tacz.melee.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            TaCZKeyCategory.TACZ);

    public static void onMeleeKeyPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == InputConstants.PRESS && MELEE_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.getKey()))) {
            doMeleeLogic();
        }
    }

    public static void onMeleeMousePress(InputEvent.MouseButton.Post event) {
        if (isInGame() && event.getAction() == InputConstants.PRESS && MELEE_KEY.matches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            doMeleeLogic();
        }
    }

    public static boolean onMeleeControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (!operator.isAim()) {
                operator.melee();
                return true;
            }
        }
        return false;
    }

    private static void doMeleeLogic() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (!operator.isAim()) {
            operator.melee();
        }
    }
}
