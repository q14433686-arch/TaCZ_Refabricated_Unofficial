package com.tacz.guns.client.gui;

import com.tacz.guns.block.entity.IndustrialSalvageStationBlockEntity;
import com.tacz.guns.inventory.IndustrialSalvageMenu;
import com.tacz.guns.network.message.ClientMessageSalvageIndustry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.NotNull;

/** Explicit recovery UI; outputs are visible before the player confirms salvage. */
public final class IndustrialSalvageScreen extends AbstractContainerScreen<IndustrialSalvageMenu> {
    public IndustrialSalvageScreen(IndustrialSalvageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.tacz.industrial_salvage.salvage"), button ->
                        ClientPlayNetworking.send(new ClientMessageSalvageIndustry(menu.containerId)))
                .bounds(leftPos + 18, topPos + 72, 62, 20)
                .build());
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE1B2228);
        gui.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 94, 0xFF303A42);
        gui.fill(leftPos + 3, topPos + 96, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xCC20272D);

        slotFrame(gui, leftPos + 28, topPos + 42);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slotFrame(gui, leftPos + 99 + column * 18, topPos + 18 + row * 18);
            }
        }
        int progress = menu.getAutoProgress();
        int width = 54 * Math.clamp(progress, 0, IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS)
                / IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 23, topPos + 31, leftPos + 77, topPos + 35, 0xFF101417);
        gui.fill(leftPos + 23, topPos + 31, leftPos + 23 + width, topPos + 35, 0xFFD36D32);

        gui.text(font, title, leftPos + 8, topPos + 8, 0xFFFFFFFF, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.input"), leftPos + 17, topPos + 20, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.output"), leftPos + 97, topPos + 12, 0xFFB9C6D0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 8, topPos + 94, 0xFFB9C6D0, false);
    }

    private static void slotFrame(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF101417);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF56636E);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF20282E);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
