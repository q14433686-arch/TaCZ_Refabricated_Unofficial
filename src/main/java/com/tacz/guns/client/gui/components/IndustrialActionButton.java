package com.tacz.guns.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * Compact industrial-console action button shared by cartridge assembly and
 * salvage screens. It deliberately has a stable visual state instead of the
 * vanilla grey rectangle so the two new machine UIs read as machinery rather
 * than temporary debug containers.
 */
public final class IndustrialActionButton extends Button {
    private final int accent;
    private final List<Component> tooltip;

    public IndustrialActionButton(int x, int y, int width, int height, Component label,
                                  int accent, Component tooltip, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.accent = accent;
        this.tooltip = List.of(tooltip);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hovered = isHoveredOrFocused();
        int fill = active ? (hovered ? 0xFF2D3942 : 0xFF202A31) : 0xFF15191C;
        int outline = active ? (hovered ? 0xFFFFFFFF : accent) : 0xFF4A5054;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF0D1114);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, outline);
        graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, fill);
        graphics.fill(getX() + 3, getY() + 3, getX() + 5, getY() + height - 3, accent);
        graphics.centeredText(font, getMessage(), getX() + width / 2 + 2, getY() + (height - 8) / 2,
                active ? 0xFFF4F7F8 : 0xFF80888D);
        if (isHovered && !tooltip.isEmpty()) {
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }
}
