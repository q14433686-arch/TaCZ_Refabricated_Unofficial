package com.tacz.guns.client.init;

import com.tacz.guns.client.gui.CartridgeAssemblyScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.IndustrialSalvageScreen;
import com.tacz.guns.client.gui.IndustrialServiceBenchScreen;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.inventory.IndustrialSalvageMenu;
import com.tacz.guns.inventory.IndustrialServiceBenchMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;

@Environment(EnvType.CLIENT)
public class ModContainerScreen {
    public static void registerScreens() {
        MenuScreens.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new);
        MenuScreens.register(CartridgeAssemblyMenu.TYPE, CartridgeAssemblyScreen::new);
        MenuScreens.register(IndustrialSalvageMenu.TYPE, IndustrialSalvageScreen::new);
        MenuScreens.register(IndustrialServiceBenchMenu.TYPE, IndustrialServiceBenchScreen::new);
    }
}
