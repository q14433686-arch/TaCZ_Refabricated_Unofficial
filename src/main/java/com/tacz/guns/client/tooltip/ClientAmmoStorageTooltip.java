package com.tacz.guns.client.tooltip;

import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public class ClientAmmoStorageTooltip implements ClientTooltipComponent {
    private static final int ENTRY_HEIGHT = 28;
    private final AmmoStorageTooltip tooltip;

    public ClientAmmoStorageTooltip(AmmoStorageTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getHeight(Font font) {
        int height = tooltip.getEntries().size() * ENTRY_HEIGHT;
        return tooltip.getHidden() > 0 ? height + 10 : height;
    }

    @Override
    public int getWidth(Font font) {
        int width = 0;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            width = Math.max(width, font.width(entry.getAmmoName()));
            width = Math.max(width, font.width(getCount(entry)));
        }
        if (tooltip.getHidden() > 0) {
            width = Math.max(width, font.width(getHidden()));
        }
        return width + 22;
    }

    @Override
    public void renderText(GuiGraphics graphics, Font font, int x, int y) {
        int yOffset = y;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            graphics.drawString(font, entry.getAmmoName(), x + 20, yOffset + 4, 0xFFFFAA00, false);
            graphics.drawString(font, getCount(entry), x + 20, yOffset + 15, 0xFF666666, false);
            yOffset += ENTRY_HEIGHT;
        }
        if (tooltip.getHidden() > 0) {
            graphics.drawString(font, getHidden(), x, yOffset, 0xFF9E9E9E, false);
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics guiGraphics) {
        int yOffset = y;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            guiGraphics.renderItem(entry.ammo(), x, yOffset + 5);
            yOffset += ENTRY_HEIGHT;
        }
    }

    private static Component getCount(AmmoStorageTooltip.Entry entry) {
        return Component.translatable("tooltip.tacz.ammo_box.count", entry.count());
    }

    private Component getHidden() {
        return Component.translatable("container.shulkerBox.more", tooltip.getHidden()).withStyle(ChatFormatting.ITALIC);
    }
}
