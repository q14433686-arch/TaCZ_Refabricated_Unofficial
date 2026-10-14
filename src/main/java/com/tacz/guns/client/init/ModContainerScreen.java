package com.tacz.guns.client.init;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.client.ClientRendererRegistration;

@Environment(EnvType.CLIENT)
public class ModContainerScreen {
    public static void registerScreens() {
        ClientRendererRegistration.menu(GunSmithTableMenu.TYPE, GunSmithTableScreen::new);
    }
}
