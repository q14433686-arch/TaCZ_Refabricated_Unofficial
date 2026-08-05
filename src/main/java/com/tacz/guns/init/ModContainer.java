package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class ModContainer {
    public static void init() {

    }

    public static final MenuType<GunSmithTableMenu> GUN_SMITH_TABLE_MENU = register("gun_smith_table_menu", GunSmithTableMenu.TYPE);
    public static final MenuType<CartridgeAssemblyMenu> CARTRIDGE_ASSEMBLY_MENU = register(
            "cartridge_assembly_menu", CartridgeAssemblyMenu.TYPE
    );

    private static <T extends AbstractContainerMenu> MenuType<T> register(String name, MenuType<T> type) {
        return Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), type);
    }
}
