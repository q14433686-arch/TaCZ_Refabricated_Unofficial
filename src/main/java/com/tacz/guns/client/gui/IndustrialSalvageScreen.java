package com.tacz.guns.client.gui;

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
 * Recovery console styled as a guarded disassembly press. The diagonal warning
 * marks and nine-cell collection tray make its destructive one-way action
 * unmistakable without hiding the returned parts from the player.
 */
public final class IndustrialSalvageScreen extends AbstractContainerScreen<IndustrialSalvageMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("tacz", "textures/gui/industrial_salvage.png");
    private static final int ORANGE = 0xFFD97537;
    private static final int GREEN = 0xFF74C694;

    public IndustrialSalvageScreen(IndustrialSalvageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(new IndustrialActionButton(leftPos + 17, topPos + 74, 64,
                Component.translatable("gui.tacz.industrial_salvage.salvage"), ORANGE,
                button -> ClientPlayNetworking.send(new ClientMessageSalvageIndustry(menu.containerId))));
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        inputBay(gui, leftPos + 28, topPos + 42);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                recoveryBay(gui, leftPos + 99 + column * 18, topPos + 18 + row * 18);
            }
        }

        // A guarded cutter rail makes the conversion visually directional.
        gui.fill(leftPos + 49, topPos + 49, leftPos + 87, topPos + 52, 0xFF834422);
        gui.fill(leftPos + 83, topPos + 45, leftPos + 87, topPos + 56, ORANGE);
        gui.fill(leftPos + 87, topPos + 47, leftPos + 92, topPos + 54, 0xFFE8A347);
        gui.fill(leftPos + 92, topPos + 49, leftPos + 97, topPos + 52, ORANGE);

        int progress = Math.clamp(menu.getAutoProgress(), 0, IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS);
        int width = 60 * progress / IndustrialSalvageStationBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 16, topPos + 64, leftPos + 76, topPos + 68, 0xFF071014);
        gui.fill(leftPos + 17, topPos + 65, leftPos + 17 + width, topPos + 67, progress > 0 ? GREEN : 0xFF4F3A2C);
        // The warning chevrons and input rim already communicate the destructive
        // action. Use one lamp rather than a sentence in the cramped cutter bay.
        gui.fill(leftPos + 76, topPos + 63, leftPos + 82, topPos + 69, 0xFF071014);
        gui.fill(leftPos + 77, topPos + 64, leftPos + 81, topPos + 68, progress > 0 ? GREEN : ORANGE);

        gui.text(font, title, leftPos + 12, topPos + 10, 0xFFF0F5EE, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.feed_bays"), leftPos + 17, topPos + 23, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.recovery_tray"), leftPos + 96, topPos + 10, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 12, topPos + 112, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.redstone_hint"), leftPos + 12, topPos + 176, 0xFF718687, false);
    }

    private static void inputBay(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x - 3, y - 3, x + 21, y + 21, 0xFF613220);
        gui.fill(x - 2, y - 2, x + 20, y + 20, 0xFFD97537);
        gui.fill(x, y, x + 18, y + 18, 0xFF071014);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF564235);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF202A2D);
    }

    private static void recoveryBay(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF081216);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5B7774);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF1B2B2D);
        gui.fill(x + 2, y + 2, x + 5, y + 3, 0xFF7CB5A4);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        // Labels share the textured panel's absolute coordinate system.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
