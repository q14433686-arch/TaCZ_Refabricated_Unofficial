package com.tacz.guns.client.gui;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.entity.IndustrialSalvageStationBlockEntity;
import com.tacz.guns.client.gui.components.IndustrialActionButton;
import com.tacz.guns.inventory.IndustrialSalvageMenu;
import com.tacz.guns.network.message.ClientMessageSalvageIndustry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Recovery-bench console for the one-input/nine-output industrial salvage flow.
 * The UI separates inspection, cutter progress and recovered-output bays so a
 * player can read why this station is not a generic furnace.
 */
public final class IndustrialSalvageScreen extends AbstractContainerScreen<IndustrialSalvageMenu> {
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            GunMod.MOD_ID, "textures/gui/industrial_salvage_console.png"
    );
    private static final Identifier MACHINE_ICON = Identifier.fromNamespaceAndPath(
            "tacz_extra", "textures/item/base_m_salvage.png"
    );
    private static final int ACCENT = 0xFFD36D32;

    public IndustrialSalvageScreen(IndustrialSalvageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 220);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(new IndustrialActionButton(
                leftPos + 24, topPos + 94, 82, 20,
                Component.translatable("gui.tacz.industrial_salvage.salvage"),
                ACCENT,
                Component.translatable("gui.tacz.industrial_salvage.salvage_hint"),
                button -> ClientPlayNetworking.send(new ClientMessageSalvageIndustry(menu.containerId))
        ));
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos,
                0, 0, imageWidth, imageHeight, 256, 256, 0xFFFFFFFF);
        gui.blit(RenderPipelines.GUI_TEXTURED, MACHINE_ICON, leftPos + 111, topPos + 9,
                0, 0, 24, 24, 32, 32, 0xFFFFFFFF);

        inputSlot(gui, leftPos + 32, topPos + 53);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                outputSlot(gui, leftPos + 149 + column * 18, topPos + 35 + row * 18,
                        row == 1 && column == 1);
            }
        }

        gui.fill(leftPos + 22, topPos + 41, leftPos + 111, topPos + 46, 0xFF10171B);
        int progressWidth = 89 * Math.clamp(menu.getAutoProgress(), 0, IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS)
                / IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 22, topPos + 41, leftPos + 22 + progressWidth, topPos + 46, ACCENT);
        gui.fill(leftPos + 116, topPos + 40, leftPos + 118, topPos + 48, ACCENT);

        gui.text(font, title, leftPos + 12, topPos + 11, 0xFFF3F6F7, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.inspect"), leftPos + 23, topPos + 31, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.input"), leftPos + 26, topPos + 75, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.output"), leftPos + 148, topPos + 17, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_salvage.status"), leftPos + 22, topPos + 51, 0xFF8EA4B0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 24, topPos + 124, 0xFFB8C9D3, false);
    }

    private static void inputSlot(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x, y, x + 20, y + 20, 0xFF0D1215);
        gui.fill(x + 1, y + 1, x + 19, y + 19, 0xFF596976);
        gui.fill(x + 2, y + 2, x + 18, y + 18, 0xFF202A30);
        gui.fill(x + 3, y + 3, x + 17, y + 4, ACCENT);
    }

    private static void outputSlot(GuiGraphicsExtractor gui, int x, int y, boolean center) {
        int frame = center ? ACCENT : 0xFF596976;
        gui.fill(x, y, x + 20, y + 20, 0xFF0D1215);
        gui.fill(x + 1, y + 1, x + 19, y + 19, frame);
        gui.fill(x + 2, y + 2, x + 18, y + 18, 0xFF202A30);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
