package com.tacz.guns.client.gui;

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
 * Cartridge assembler console: four numbered material bays feed a visible
 * routing rail, then one protected output bay. It intentionally makes the
 * four-item contract legible before a player opens a recipe viewer.
 */
public final class CartridgeAssemblyScreen extends AbstractContainerScreen<CartridgeAssemblyMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("tacz", "textures/gui/cartridge_assembly.png");
    private static final int AMBER = 0xFFE6A63B;
    private static final int GREEN = 0xFF74C694;

    public CartridgeAssemblyScreen(CartridgeAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
    }

    @Override
    public void init() {
        super.init();
        addRenderableWidget(new IndustrialActionButton(leftPos + 92, topPos + 74, 72,
                Component.translatable("gui.tacz.cartridge_assembly.assemble"), AMBER,
                button -> ClientPlayNetworking.send(new ClientMessageAssembleCartridge(menu.containerId))));
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        // Four material bays: the coloured corner is a small "socket number",
        // so the grid reads as a process rather than five unrelated slots.
        materialBay(gui, leftPos + 25, topPos + 34, "01", AMBER);
        materialBay(gui, leftPos + 61, topPos + 34, "02", AMBER);
        materialBay(gui, leftPos + 25, topPos + 61, "03", AMBER);
        materialBay(gui, leftPos + 61, topPos + 61, "04", AMBER);
        outputBay(gui, leftPos + 131, topPos + 47);

        // The copper routing rail is a compact visual explanation: all four
        // bays converge, are processed, then leave through the secured output.
        gui.fill(leftPos + 83, topPos + 49, leftPos + 88, topPos + 51, 0xFF775126);
        gui.fill(leftPos + 83, topPos + 75, leftPos + 88, topPos + 77, 0xFF775126);
        gui.fill(leftPos + 86, topPos + 50, leftPos + 88, topPos + 76, 0xFF775126);
        gui.fill(leftPos + 88, topPos + 62, leftPos + 124, topPos + 65, 0xFF8D612C);
        gui.fill(leftPos + 121, topPos + 59, leftPos + 124, topPos + 68, 0xFF8D612C);
        gui.fill(leftPos + 124, topPos + 62, leftPos + 128, topPos + 65, 0xFFB9823B);

        int progress = Math.clamp(menu.getAutoProgress(), 0, CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS);
        int progressWidth = 46 * progress / CartridgeAssemblyMachineBlockEntity.AUTO_PROCESS_TICKS;
        gui.fill(leftPos + 92, topPos + 55, leftPos + 138, topPos + 59, 0xFF071014);
        gui.fill(leftPos + 93, topPos + 56, leftPos + 93 + progressWidth, topPos + 58, progress > 0 ? GREEN : 0xFF314047);
        gui.text(font, Component.translatable(progress > 0
                        ? "gui.tacz.industrial_console.auto_cycle"
                        : "gui.tacz.industrial_console.manual_ready"),
                leftPos + 92, topPos + 45, progress > 0 ? GREEN : 0xFFB5C5C4, false);

        gui.text(font, title, leftPos + 12, topPos + 10, 0xFFF0F5EE, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.feed_bays"), leftPos + 13, topPos + 23, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.case"), leftPos + 15, topPos + 85, 0xFFBFCACA, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.projectile"), leftPos + 49, topPos + 85, 0xFFBFCACA, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.primer"), leftPos + 13, topPos + 96, 0xFFBFCACA, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.propellant"), leftPos + 47, topPos + 96, 0xFFBFCACA, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.secured_output"), leftPos + 119, topPos + 26, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 12, topPos + 112, 0xFF91A9A9, false);
        gui.text(font, Component.translatable("gui.tacz.industrial_console.redstone_hint"), leftPos + 12, topPos + 176, 0xFF718687, false);
    }

    private void materialBay(GuiGraphicsExtractor gui, int x, int y, String number, int accent) {
        gui.fill(x, y, x + 18, y + 18, 0xFF071014);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF66777A);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF1A2A2F);
        gui.fill(x, y, x + 7, y + 7, accent);
        gui.text(font, Component.literal(number), x + 1, y, 0xFF1B211E, false);
    }

    private static void outputBay(GuiGraphicsExtractor gui, int x, int y) {
        gui.fill(x - 2, y - 2, x + 20, y + 20, 0xFF805D2C);
        gui.fill(x - 1, y - 1, x + 19, y + 19, 0xFFE3A43B);
        gui.fill(x, y, x + 18, y + 18, 0xFF091216);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF31464A);
        gui.fill(x + 2, y + 2, x + 16, y + 16, 0xFF17262B);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        // All labels use absolute coordinates alongside their custom panels.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
