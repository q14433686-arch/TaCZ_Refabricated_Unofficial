package cn.sh1rocu.tacz.client;

import net.minecraft.client.core.registries.BuiltInClientRegistries;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Snapshot 4 stores renderer and menu factories in client registries. */
public final class ClientRendererRegistration {
    private ClientRendererRegistration() {}

    public static <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
        Registry.register(BuiltInClientRegistries.ENTITY_RENDERER, BuiltInRegistries.ENTITY_TYPE.getKey(type), provider);
    }

    public static <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntity(
            BlockEntityType<T> type, BlockEntityRendererProvider<T, S> provider) {
        Registry.register(BuiltInClientRegistries.BLOCK_ENTITY_RENDERER, BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type), provider);
    }

    public static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menu(
            MenuType<M> type, MenuScreens.ScreenConstructor<M, U> provider) {
        Registry.register(BuiltInClientRegistries.MENU_SCREEN, BuiltInRegistries.MENU.getKey(type), provider);
    }
}
