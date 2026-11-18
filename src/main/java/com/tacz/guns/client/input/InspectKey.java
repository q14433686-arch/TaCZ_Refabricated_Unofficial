package com.tacz.guns.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class InspectKey {
    public static final KeyMapping INSPECT_KEY = new KeyMapping("key.tacz.inspect.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H,
            TaCZKeyCategory.TACZ);

    public static boolean onInspectControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).inspect();
            return true;
        }
        return false;
    }
}
