package com.tacz.guns.client.gui;

import com.tacz.guns.client.gui.components.IndustrialActionButton;
import com.tacz.guns.inventory.IndustrialServiceBenchMenu;
import com.tacz.guns.network.message.ClientMessageServiceIndustry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Deliberately exposes all ten real service slots: no recipe list or fake
 * progress overlay can replace the five physical component bays.
 */
public final class IndustrialServiceBenchScreen extends AbstractContainerScreen<IndustrialServiceBenchMenu> {
    private static final int ACCENT = 0xFF4EA8A8;

    public IndustrialServiceBenchScreen(IndustrialServiceBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 220);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(new IndustrialActionButton(leftPos + 16, topPos + 103, 93, 20,
                Component.translatable("gui.tacz.industrial_service.disassemble"), ACCENT,
                Component.translatable("gui.tacz.industrial_service.disassemble_hint"),
                button -> ClientPlayNetworking.send(new ClientMessageServiceIndustry(menu.containerId, false))));
        addRenderableWidget(new IndustrialActionButton(leftPos + 121, topPos + 103, 93, 20,
                Component.translatable("gui.tacz.industrial_service.reassemble"), ACCENT,
                Component.translatable("gui.tacz.industrial_service.reassemble_hint"),
                button -> ClientPlayNetworking.send(new ClientMessageServiceIndustry(menu.containerId, true))));
    }

    @Override
    protected void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF12191C);
        gui.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF26343A);
        gui.fill(leftPos + 10, topPos + 17, leftPos + imageWidth - 10, topPos + 19, ACCENT);

        slot(gui, leftPos + 23, topPos + 41, ACCENT);
        slot(gui, leftPos + 58, topPos + 23, 0xFF8EA4B0);
        slot(gui, leftPos + 94, topPos + 23, 0xFF8EA4B0);
        slot(gui, leftPos + 130, topPos + 23, 0xFFF2C14E);
        for (int index = 0; index < 5; index++) slot(gui, leftPos + 50 + index * 18, topPos + 73, ACCENT);
        slot(gui, leftPos + 179, topPos + 41, 0xFF65C466);

        gui.text(font, title, leftPos + 12, topPos + 7, 0xFFF3F6F7, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_service.gun_input"), leftPos + 15, topPos + 29, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_service.tooling"), leftPos + 59, topPos + 7, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_service.components"), leftPos + 50, topPos + 56, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_service.gun_output"), leftPos + 169, topPos + 29, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 24, topPos + 124, 0xFFB8C9D3, false);
    }

    private static void slot(GuiGraphicsExtractor gui, int x, int y, int frame) {
        gui.fill(x, y, x + 20, y + 20, 0xFF0D1215);
        gui.fill(x + 1, y + 1, x + 19, y + 19, frame);
        gui.fill(x + 2, y + 2, x + 18, y + 18, 0xFF202A30);
    }

    @Override protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {}
    @Override public boolean isPauseScreen() { return false; }
}
