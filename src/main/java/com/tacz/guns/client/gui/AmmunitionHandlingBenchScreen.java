package com.tacz.guns.client.gui;

import com.tacz.guns.block.entity.AmmunitionHandlingBenchBlockEntity;
import com.tacz.guns.client.gui.components.IndustrialActionButton;
import com.tacz.guns.inventory.AmmunitionHandlingBenchMenu;
import com.tacz.guns.network.message.ClientMessageHandleAmmunition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Visualises the real carrier, four independently selectable input lanes, tool
 * and four output lanes. Buttons only request a timed server transaction; they
 * never move an ItemStack on the client.
 */
public final class AmmunitionHandlingBenchScreen extends AbstractContainerScreen<AmmunitionHandlingBenchMenu> {
    private static final int ACCENT = 0xFFB27A3C;
    private static final int LOAD_ACCENT = 0xFF6DAFD4;
    private static final int UNLOAD_ACCENT = 0xFF70BA7D;

    public AmmunitionHandlingBenchScreen(AmmunitionHandlingBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 240);
    }

    @Override
    public void init() {
        super.init();
        for (int input = 0; input < AmmunitionHandlingBenchBlockEntity.INPUT_COUNT; input++) {
            int x = leftPos + 61 + input * 18;
            int source = input;
            addRenderableWidget(new IndustrialActionButton(x, topPos + 63, 17, 16, Component.literal("+"), LOAD_ACCENT,
                    Component.translatable("gui.tacz.ammo_handling.load_one", source + 1),
                    button -> send(AmmunitionHandlingBenchBlockEntity.ACTION_LOAD_ONE, source)));
            addRenderableWidget(new IndustrialActionButton(x, topPos + 80, 17, 16, Component.literal("∞"), LOAD_ACCENT,
                    Component.translatable("gui.tacz.ammo_handling.load_continuous", source + 1),
                    button -> send(AmmunitionHandlingBenchBlockEntity.ACTION_LOAD_CONTINUOUS, source)));
        }
        addRenderableWidget(new IndustrialActionButton(leftPos + 149, topPos + 40, 65, 18,
                Component.translatable("gui.tacz.ammo_handling.unload_one"), UNLOAD_ACCENT,
                Component.translatable("gui.tacz.ammo_handling.unload_one_hint"),
                button -> send(AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_ONE, 0)));
        addRenderableWidget(new IndustrialActionButton(leftPos + 149, topPos + 61, 65, 18,
                Component.translatable("gui.tacz.ammo_handling.unload_continuous"), UNLOAD_ACCENT,
                Component.translatable("gui.tacz.ammo_handling.unload_continuous_hint"),
                button -> send(AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_CONTINUOUS, 0)));
        addRenderableWidget(new IndustrialActionButton(leftPos + 149, topPos + 82, 65, 18,
                Component.translatable("gui.tacz.ammo_handling.cancel"), 0xFFD16B58,
                Component.translatable("gui.tacz.ammo_handling.cancel_hint"),
                button -> send(AmmunitionHandlingBenchBlockEntity.ACTION_CANCEL, 0)));
    }

    private void send(int action, int input) {
        ClientPlayNetworking.send(new ClientMessageHandleAmmunition(menu.containerId, action, input));
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF12191C);
        gui.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF26343A);
        gui.fill(leftPos + 10, topPos + 17, leftPos + imageWidth - 10, topPos + 19, ACCENT);

        framedSlot(gui, leftPos + 21, topPos + 39, ACCENT);
        for (int input = 0; input < AmmunitionHandlingBenchBlockEntity.INPUT_COUNT; input++) {
            framedSlot(gui, leftPos + 61 + input * 18, topPos + 39, LOAD_ACCENT);
        }
        framedSlot(gui, leftPos + 21, topPos + 99, 0xFFE1C15A);
        for (int output = 0; output < AmmunitionHandlingBenchBlockEntity.OUTPUT_COUNT; output++) {
            framedSlot(gui, leftPos + 61 + output * 18, topPos + 99, UNLOAD_ACCENT);
        }

        int duration = Math.max(0, menu.getOperationDuration());
        int progress = Math.clamp(menu.getOperationProgress(), 0, duration);
        int width = duration <= 0 ? 0 : 116 * progress / duration;
        gui.fill(leftPos + 22, topPos + 130, leftPos + 138, topPos + 136, 0xFF0B1013);
        gui.fill(leftPos + 22, topPos + 130, leftPos + 22 + width, topPos + 136,
                menu.getOperationActionId() == AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_ONE
                        || menu.getOperationActionId() == AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_CONTINUOUS
                        ? UNLOAD_ACCENT : LOAD_ACCENT);

        gui.text(font, title, leftPos + 12, topPos + 7, 0xFFF3F6F7, false);
        gui.text(font, Component.translatable("gui.tacz.ammo_handling.carrier"), leftPos + 15, topPos + 26, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.ammo_handling.inputs"), leftPos + 61, topPos + 26, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.ammo_handling.tool"), leftPos + 14, topPos + 87, 0xFFE2C673, false);
        gui.text(font, Component.translatable("gui.tacz.ammo_handling.outputs"), leftPos + 61, topPos + 87, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable(statusKey(menu.getOperationActionId())), leftPos + 22, topPos + 120, 0xFFB8C9D3, false);
        gui.text(font, Component.translatable("gui.tacz.cartridge_assembly.inventory"), leftPos + 24, topPos + 140, 0xFFB8C9D3, false);
    }

    private static String statusKey(int action) {
        return switch (action) {
            case AmmunitionHandlingBenchBlockEntity.ACTION_LOAD_ONE,
                    AmmunitionHandlingBenchBlockEntity.ACTION_LOAD_CONTINUOUS -> "gui.tacz.ammo_handling.status.loading";
            case AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_ONE,
                    AmmunitionHandlingBenchBlockEntity.ACTION_UNLOAD_CONTINUOUS -> "gui.tacz.ammo_handling.status.unloading";
            default -> "gui.tacz.ammo_handling.status.idle";
        };
    }

    private static void framedSlot(GuiGraphicsExtractor gui, int x, int y, int frame) {
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
