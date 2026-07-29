package com.tacz.guns.client.gui.components.smith;

import com.tacz.guns.GunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class ResultButton extends Button {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table.png");
    private final ItemStack stack;
    private boolean isSelected = false;

    public ResultButton(int pX, int pY, ItemStack stack, Button.OnPress onPress) {
        super(pX, pY, 94, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        this.stack = stack;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gui, int pMouseX, int pMouseY, float pPartialTick) {
        if (isSelected) {
            if (isHoveredOrFocused()) {
                gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() - 1, this.getY() - 1, 52, 229, this.width + 2, this.height + 2, 256, 256);
            } else {
                gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 53, 230, this.width, this.height, 256, 256);
            }
        } else {
            if (isHoveredOrFocused()) {
                gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() - 1, this.getY() - 1, 52, 211, this.width + 2, this.height + 2, 256, 256);
            } else {
                gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 53, 212, this.width, this.height, 256, 256);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        gui.item(stack, this.getX() + 1, this.getY());

        // 第 15 轮：防止长名称溢出按钮（按钮宽 94，图标占到 x+20，右侧留 2px 余量）。
        // 原先直接整串绘制，像 ".30-06 孤星 手炮" 这类长名会画到按钮外面去。
        Component hoverName = this.stack.getHoverName();
        int maxWidth = this.width - 20 - 2;
        String name = hoverName.getString();
        if (mc.font.width(name) > maxWidth) {
            // 用原版的 plainSubstrByWidth 截断并补省略号，注意要给 "..." 预留宽度
            name = mc.font.plainSubstrByWidth(name, maxWidth - mc.font.width("...")) + "...";
        }
        gui.text(mc.font, name, this.getX() + 20, this.getY() + 4, 0xFFFFFFFF);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.isSelected = true;
        this.onPress.onPress(this);
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public void renderTooltips(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused() && !this.stack.isEmpty()) {
            consumer.accept(this.stack);
        }
    }
}
