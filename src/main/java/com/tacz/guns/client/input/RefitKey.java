package com.tacz.guns.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class RefitKey {
    public static final KeyMapping REFIT_KEY = new KeyMapping("key.tacz.refit.desc",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            TaCZKeyCategory.TACZ);

    static void onRefitPress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }
        if (isInGame()) {
            if (IGun.mainHandHoldGun(player) && Minecraft.getInstance().screen == null) {
                IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
                if (iGun != null && iGun.hasAttachmentLock(player.getMainHandItem())) {
                    return;
                }
                Minecraft.getInstance().setScreen(new GunRefitScreen());
            }
        }
    }
}
