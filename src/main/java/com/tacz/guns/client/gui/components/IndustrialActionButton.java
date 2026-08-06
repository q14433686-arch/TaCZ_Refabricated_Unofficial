package com.tacz.guns.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Compact, tactile action control shared by TACZ industrial machines.
 *
 * <p>It deliberately avoids vanilla's pale rounded button: the machine panels
 * use a recessed steel control with a coloured status strip instead. The
 * rendering is code-driven so the accent can also communicate each station's
 * job without requiring a separate texture for every button state.</p>
 */
public final class IndustrialActionButton extends Button {
    private final int accent;

    public IndustrialActionButton(int x, int y, int width, Component message, int accent, OnPress onPress) {
        super(x, y, width, 18, message, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int right = x + width;
        int bottom = y + height;
        boolean active = isActive();
        boolean hovered = isHoveredOrFocused();

        // Raised rim, recessed face, and a narrow coloured "armed" strip.
        gui.fill(x, y, right, bottom, active ? 0xFF071116 : 0xFF15191B);
        gui.fill(x + 1, y + 1, right - 1, bottom - 1, active ? 0xFF5A6970 : 0xFF384247);
        gui.fill(x + 2, y + 2, right - 2, bottom - 2, active ? 0xFF16252B : 0xFF202629);
        gui.fill(x + 3, y + 3, x + 5, bottom - 3, active ? accent : 0xFF5E686B);
        gui.fill(x + 6, bottom - 4, right - 3, bottom - 3, active ? 0xFF0A1114 : 0xFF161B1E);
        if (hovered && active) {
            gui.fill(x + 1, y + 1, right - 1, y + 2, 0xFFEAF4E8);
            gui.fill(x + 1, y + 1, x + 2, bottom - 1, 0xFFEAF4E8);
            gui.fill(right - 2, y + 1, right - 1, bottom - 1, 0xFFB7CDC4);
        }
        gui.centeredText(Minecraft.getInstance().font, getMessage(), x + width / 2 + 2,
                y + (height - 8) / 2, active ? 0xFFE7F0EC : 0xFF8B9495);
    }
}
