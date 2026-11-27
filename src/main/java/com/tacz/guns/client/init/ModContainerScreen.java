package com.tacz.guns.client.init;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;

@Environment(EnvType.CLIENT)
public class ModContainerScreen {
    public static void registerScreens() {
        MenuScreens.register(com.tacz.guns.inventory.AmmoStorageMenu.TYPE, com.tacz.guns.client.gui.AmmoStorageScreen::new);
        MenuScreens.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new);
    }
}
