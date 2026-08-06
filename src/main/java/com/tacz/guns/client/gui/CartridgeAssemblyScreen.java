package com.tacz.guns.client.gui;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import com.tacz.guns.client.gui.components.IndustrialActionButton;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.network.message.ClientMessageAssembleCartridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Four-bay industrial cartridge console.
 *
 * <p>The screen intentionally visualizes the real server-authoritative bays
 * rather than pretending final loading is a generic crafting grid: case,
 * projectile, primer and propellant feed one monitored output station.</p>
 */
public final class CartridgeAssemblyScreen extends AbstractContainerScreen<CartridgeAssemblyMenu> {
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            GunMod.MOD_ID, "textures/gui/cartridge_assembly_console.png"
    );
    private static final Identifier MACHINE_ICON = Identifier.fromNamespaceAndPath(
            "tacz_extra", "textures/item/base_m_assembly.png"
    );
    private static final int ACCENT = 0xFFDF9A32;

    public CartridgeAssemblyScreen(CartridgeAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 220);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(new IndustrialActionButton(
                leftPos + 106, topPos + 91, 70, 20,
                Component.translatable("gui.tacz.cartridge_assembly.assemble"),
                ACCENT,
                Component.translatable("gui.tacz.cartridge_assembly.assemble_hint"),
                button -> ClientPlayNetworking.send(new ClientMessageAssembleCartridge(menu.containerId))
        ));
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos,
                0, 0, imageWidth, imageHeight, 256, 256, 0xFFFFFFFF);
        gui.blit(RenderPipelines.GUI_TEXTURED, MACHINE_ICON, leftPos + 191, topPos + 11,
                0, 0, 24, 24, 32, 32, 0xFFFFFFFF);

        // Four physical feed bays, left to right/top to bottom. Slots stay in
        // the menu at the exact framed coordinates below.
        industrialSlot(gui, leftPos + 30, topPos + 51, ACCENT);
        industrialSlot(gui, leftPos + 66, topPos + 51, 0xFF76A9D8);
        industrialSlot(gui, leftPos + 30, topPos + 87, 0xFFD2C56C);
        industrialSlot(gui, leftPos + 66, topPos + 87, 0xFFCE7445);
        outputSlot(gui, leftPos + 183, topPos + 63);

        gui.fill(leftPos + 101, topPos + 50, leftPos + 174, topPos + 52, 0xFF52616B);
        gui.fill(leftPos + 101, topPos + 51, leftPos + 174, topPos + 52, ACCENT);
        gui.fill(leftPos + 101, topPos + 70, leftPos + 174, topPos + 75, 0xFF10171B);
        int progressWidth = 73 * Math.clamp(menu.getAutoProgress(), 0, CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS)
                / CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 101, topPos + 70, leftPos + 101 + progressWidth, topPos + 75, ACCENT);
        gui.fill(leftPos + 178, topPos + 69, leftPos + 180, topPos + 77, ACCENT);

        gui.text(font, title, leftPos + 12, topPos + 11, 0xFFF3F6F7, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.bays"), leftPos + 12, topPos + 31, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.case"), leftPos + 22, topPos + 40, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.projectile"), leftPos + 54, topPos + 40, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.primer"), leftPos + 20, topPos + 109, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.propellant"), leftPos + 53, topPos + 109, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.output"), leftPos + 177, topPos + 45, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.status"), leftPos + 101, topPos + 59, 0xFF8EA4B0, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 24, topPos + 124, 0xFFB8C9D3, false);
    }

    private static void industrialSlot(GuiGraphicsExtractor gui, int x, int y, int accent) {
        gui.fill(x, y, x + 20, y + 20, 0xFF0D1215);
        gui.fill(x + 1, y + 1, x + 19, y + 19, 0xFF596976);
        gui.fill(x + 2, y + 2, x + 18, y + 18, 0xFF1D262D);
        gui.fill(x + 3, y + 3, x + 17, y + 4, accent);
    }

    private static void outputSlot(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x, y, x + 20, y + 20, 0xFF0D1215);
        gui.fill(x + 1, y + 1, x + 19, y + 19, ACCENT);
        gui.fill(x + 2, y + 2, x + 18, y + 18, 0xFF222D34);
        gui.fill(x + 7, y + 5, x + 13, y + 7, ACCENT);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        // All labels use absolute panel coordinates so they stay aligned with
        // menu slot frames during the 26.2 extract/render split.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
