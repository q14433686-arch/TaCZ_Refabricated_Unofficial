package me.xjqsh.lrtactical.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import me.xjqsh.lrtactical.util.TooltipLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 26.2 extracted-GUI implementation of LRTactical's expandable data tooltip. */
public abstract class AbstractClientItemTooltip implements ClientTooltipComponent {
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_DESC_WIDTH = 300;
    private static final int MAX_VISIBLE_DESC_LINES = 3;
    private static final int MAX_VISIBLE_EFFECT_LINES = 3;

    private @Nullable List<FormattedCharSequence> description;
    private final List<Component> normalLines = new ArrayList<>();
    private final List<Component> effectLines = new ArrayList<>();
    private int maxWidth;

    protected void build(@Nullable String tooltipKey, List<TooltipLine> lines) {
        Font font = Minecraft.getInstance().font;
        if (tooltipKey != null && !tooltipKey.isEmpty()) {
            this.description = font.split(Component.translatable(tooltipKey), MAX_DESC_WIDTH);
            this.description.forEach(line -> this.maxWidth = Math.max(this.maxWidth, font.width(line)));
        }
        for (TooltipLine line : lines) {
            this.maxWidth = Math.max(this.maxWidth, font.width(line.text()));
            (line.collapsible() ? this.effectLines : this.normalLines).add(line.text());
        }
        if (this.effectLines.size() > MAX_VISIBLE_EFFECT_LINES) {
            this.maxWidth = Math.max(this.maxWidth, font.width(Component.translatable(
                    "tooltip.lrtactical.more_lines",
                    this.effectLines.size() - MAX_VISIBLE_EFFECT_LINES)));
        }
        if (hasCollapsedContent()) {
            this.maxWidth = Math.max(this.maxWidth,
                    font.width(Component.translatable("tooltip.lrtactical.shift_hint")));
        }
    }

    private boolean hasCollapsedContent() {
        return this.effectLines.size() > MAX_VISIBLE_EFFECT_LINES
                || this.description != null && this.description.size() > MAX_VISIBLE_DESC_LINES;
    }

    private List<FormattedCharSequence> visibleDescription() {
        if (this.description == null) {
            return List.of();
        }
        return isShiftDown() || this.description.size() <= MAX_VISIBLE_DESC_LINES
                ? this.description
                : this.description.subList(0, MAX_VISIBLE_DESC_LINES);
    }

    private List<Component> visibleLines() {
        List<Component> output = new ArrayList<>(this.normalLines);
        boolean collapseEffects = !isShiftDown()
                && this.effectLines.size() > MAX_VISIBLE_EFFECT_LINES;
        if (collapseEffects) {
            output.addAll(this.effectLines.subList(0, MAX_VISIBLE_EFFECT_LINES));
            output.add(Component.translatable("tooltip.lrtactical.more_lines",
                    this.effectLines.size() - MAX_VISIBLE_EFFECT_LINES));
        } else {
            output.addAll(this.effectLines);
        }
        if (!isShiftDown() && hasCollapsedContent()) {
            output.add(Component.translatable("tooltip.lrtactical.shift_hint"));
        }
        return output;
    }

    private static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
    }

    @Override
    public int getHeight(Font font) {
        int descriptionHeight = visibleDescription().isEmpty()
                ? 0 : visibleDescription().size() * LINE_HEIGHT + 2;
        return descriptionHeight + visibleLines().size() * LINE_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return this.maxWidth;
    }

    @Override
    public void renderText(GuiGraphics graphics, Font font, int x, int y) {
        int yOffset = y;
        List<FormattedCharSequence> description = visibleDescription();
        if (!description.isEmpty()) {
            yOffset += 2;
            for (FormattedCharSequence line : description) {
                graphics.drawString(font, line, x, yOffset, 0xFFAAAAAA);
                yOffset += LINE_HEIGHT;
            }
        }
        for (Component line : visibleLines()) {
            graphics.drawString(font, line, x, yOffset, 0xFFFFAA00);
            yOffset += LINE_HEIGHT;
        }
    }
}
