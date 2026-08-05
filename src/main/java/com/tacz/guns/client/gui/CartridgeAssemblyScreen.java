package com.tacz.guns.client.gui;

import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.network.message.ClientMessageAssembleCartridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/** Simple explicit-slot GUI for the dedicated final cartridge assembler. */
public final class CartridgeAssemblyScreen extends AbstractContainerScreen<CartridgeAssemblyMenu> {
    public CartridgeAssemblyScreen(CartridgeAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.tacz.cartridge_assembly.assemble"), button ->
                        ClientPlayNetworking.send(new ClientMessageAssembleCartridge(menu.containerId)))
                .bounds(leftPos + 92, topPos + 74, 72, 20)
                .build());
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE1B2228);
        gui.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 94, 0xFF303A42);
        gui.fill(leftPos + 3, topPos + 96, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xCC20272D);

        slotFrame(gui, leftPos + 25, topPos + 34);
        slotFrame(gui, leftPos + 61, topPos + 34);
        slotFrame(gui, leftPos + 25, topPos + 61);
        slotFrame(gui, leftPos + 61, topPos + 61);
        slotFrame(gui, leftPos + 131, topPos + 47);

        int autoProgress = menu.getAutoProgress();
        int autoWidth = 32 * Math.clamp(autoProgress, 0, CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS)
                / CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 92, topPos + 62, leftPos + 124, topPos + 67, 0xFF11171B);
        gui.fill(leftPos + 92, topPos + 62, leftPos + 92 + autoWidth, topPos + 67, 0xFFDF9A32);

        gui.text(font, title, leftPos + 8, topPos + 8, 0xFFFFFFFF, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.case"), leftPos + 18, topPos + 23, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.projectile"), leftPos + 49, topPos + 23, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.primer"), leftPos + 17, topPos + 84, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.propellant"), leftPos + 48, topPos + 84, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.output"), leftPos + 123, topPos + 35, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 8, topPos + 94, 0xFFB9C6D0, false);
    }

    private static void slotFrame(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF101417);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF56636E);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF20282E);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        // Labels are drawn in extractBackground with absolute coordinates so
        // they remain aligned with the explicitly framed slots.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
