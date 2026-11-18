package com.tacz.guns.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import com.tacz.guns.inventory.AmmoStorageEntry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.message.ClientMessageAmmoStorageClick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class AmmoStorageScreen extends AbstractContainerScreen<AmmoStorageMenu> {
    private static final Identifier CONTAINER_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int CONTAINER_ROWS = AmmoStorageMenu.STORAGE_ROWS;
    private int virtualClickButton = -1;

    public AmmoStorageScreen(AmmoStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + CONTAINER_ROWS * 18 + AmmoStorageMenu.STATUS_HEIGHT);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int storageHeight = CONTAINER_ROWS * 18 + 17 + AmmoStorageMenu.STATUS_HEIGHT;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, leftPos, topPos, 0, 0, imageWidth, 17, 256, 256);
        graphics.fill(leftPos, topPos + 17, leftPos + imageWidth, topPos + 17 + AmmoStorageMenu.STATUS_HEIGHT, 0xFFC6C6C6);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, leftPos, topPos + 17 + AmmoStorageMenu.STATUS_HEIGHT,
                0, 17, imageWidth, CONTAINER_ROWS * 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, leftPos, topPos + storageHeight, 0, 126, imageWidth, 96, 256, 256);
        renderCapacity(graphics);
        var entries = menu.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            AmmoStorageEntry entry = entries.get(i);
            int x = leftPos + AmmoStorageMenu.INVENTORY_X + i % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = topPos + AmmoStorageMenu.STORAGE_Y + i / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            graphics.item(entry.sample(), x, y);
            renderSizeLabel(graphics, x, y, formatCount(entry.count()));
        }
        int hovered = getVirtualCell(mouseX, mouseY);
        if (hovered >= 0) {
            int x = leftPos + AmmoStorageMenu.INVENTORY_X + hovered % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = topPos + AmmoStorageMenu.STORAGE_Y + hovered / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            graphics.fill(x, y, x + 16, y + 16, 0x66FFFFFF);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (isHovering(8, 17, 160, 21, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.tacz.ammo_storage.capacity_detail", menu.getUsedStacks(), menu.getCapacityStacks()),
                    Component.translatable("gui.tacz.ammo_storage.free_capacity", Math.max(0, menu.getCapacityStacks() - menu.getUsedStacks()))
            ), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        int cell = getVirtualCell(mouseX, mouseY);
        if (cell >= 0 && cell < menu.getEntries().size() && menu.getCarried().isEmpty()) {
            AmmoStorageEntry entry = menu.getEntries().get(cell);
            var tooltip = new ArrayList<>(getTooltipFromContainerItem(entry.sample()));
            tooltip.add(Component.translatable("gui.tacz.ammo_storage.count", entry.count()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.tacz.ammo_storage.remaining", entry.remainingCapacity())
                    .withStyle(entry.remainingCapacity() == 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
            graphics.setTooltipForNextFrame(font, tooltip, entry.sample().getTooltipImage(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        int cell = getVirtualCell(mouseX, mouseY);
        if (cell >= 0 && (button == 0 || button == 1)) {
            int entryId = cell < menu.getEntries().size() ? menu.getEntries().get(cell).id() : -1;
            AmmoStorageMenu.Action action = (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) ? AmmoStorageMenu.Action.QUICK_MOVE
                    : button == 1 ? AmmoStorageMenu.Action.PICKUP_HALF : AmmoStorageMenu.Action.PICKUP;
            ClientPlayNetworking.send(new ClientMessageAmmoStorageClick(menu.containerId, entryId, action));
            virtualClickButton = button;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return virtualClickButton >= 0 || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == virtualClickButton) {
            virtualClickButton = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private int getVirtualCell(double mouseX, double mouseY) {
        for (int cell = 0; cell < AmmoStorageMenu.STORAGE_SLOT_COUNT; cell++) {
            int x = AmmoStorageMenu.INVENTORY_X + cell % AmmoStorageMenu.STORAGE_COLUMNS * 18;
            int y = AmmoStorageMenu.STORAGE_Y + cell / AmmoStorageMenu.STORAGE_COLUMNS * 18;
            if (isHovering(x, y, 16, 16, mouseX, mouseY)) {
                return cell;
            }
        }
        return -1;
    }

    private void renderCapacity(GuiGraphicsExtractor graphics) {
        Component capacity = Component.translatable("gui.tacz.ammo_storage.capacity", menu.getUsedStacks(), menu.getCapacityStacks());
        Component types = Component.translatable("gui.tacz.ammo_storage.types", menu.getEntries().size(), AmmoStorageMenu.STORAGE_SLOT_COUNT);
        graphics.text(font, capacity, leftPos + 8, topPos + 18, 0xFF404040, false);
        graphics.text(font, types, leftPos + imageWidth - 8 - font.width(types), topPos + 18,
                menu.getEntries().size() == AmmoStorageMenu.STORAGE_SLOT_COUNT ? 0xFFAA3030 : 0xFF404040, false);
        int x = leftPos + 8;
        int y = topPos + 31;
        double fullness = menu.getCapacityStacks() <= 0 ? 0
                : Math.min(1, (double) menu.getUsedStacks() / menu.getCapacityStacks());
        int color = fullness >= 1 ? 0xFFC45A48 : fullness >= 0.75 ? 0xFFD7A744 : 0xFF65A85C;
        graphics.fill(x, y, x + 160, y + 6, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 159, y + 5, 0xFF777777);
        graphics.fill(x + 1, y + 1, x + 1 + (int) Math.round(158 * fullness), y + 5, color);
    }

    private String formatCount(long count) {
        if (count >= 1_000_000_000) {
            return formatCompact(count, 1_000_000_000, "b");
        }
        if (count >= 1_000_000) {
            return formatCompact(count, 1_000_000, "m");
        }
        if (count >= 1_000) {
            return formatCompact(count, 1_000, "k");
        }
        return Long.toString(count);
    }

    private String formatCompact(long count, int unit, String suffix) {
        long whole = count / unit;
        long decimal = count % unit / (unit / 10);
        return decimal == 0 || whole >= 10 ? whole + suffix : whole + "." + decimal + suffix;
    }

    private void renderSizeLabel(GuiGraphicsExtractor graphics, int x, int y, String text) {
        float scale = 0.666F;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, (int) ((x + 17 - font.width(text) * scale) / scale),
                (int) ((y + 12) / scale), 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
