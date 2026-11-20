package com.tacz.guns.client.gui.components.refit;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class InventoryAttachmentSlot extends Button implements IStackTooltip {
    private final int slotIndex;
    private final Inventory inventory;
    private final ItemStack preview;

    public InventoryAttachmentSlot(int pX, int pY, int slotIndex, Inventory inventory, Button.OnPress onPress) {
        super(pX, pY, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.preview = ItemStack.EMPTY;
        this.slotIndex = slotIndex;
        this.inventory = inventory;
    }

    public InventoryAttachmentSlot(int x, int y, ItemStack preview, Inventory inventory, Button.OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.preview = preview;
        this.inventory = inventory;
        this.slotIndex = -1;
    }

    public ItemStack getItemStack() {
        return slotIndex < 0 ? preview : inventory.getItem(slotIndex);
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused()) consumer.accept(getItemStack());
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int pMouseX, int pMouseY, float pPartialTick) {
        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        graphics.renderItem(getItemStack(), x + 1, y + 1);
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
