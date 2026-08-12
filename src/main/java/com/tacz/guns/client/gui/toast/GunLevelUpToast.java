package com.tacz.guns.client.gui.toast;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Renderable form of upstream's planned weapon-level toast.
 *
 * <p>The 26.2 rendering port is complete, but the upstream progression system that
 * would create this toast is not: {@code ServerMessageLevelUp} has no sender and
 * every level curve method currently returns zero. This class is therefore dormant.</p>
 */
@Environment(EnvType.CLIENT)
public class GunLevelUpToast implements Toast {
    private final Component title;
    private final Component subTitle;
    private final ItemStack icon;
    private long visibleTime = -1;

    public GunLevelUpToast(ItemStack icon, Component titleComponent, @Nullable Component subtitle) {
        this.icon = icon;
        this.title = titleComponent;
        this.subTitle = subtitle;
    }

    @Override
    public Visibility getWantedVisibility() {
        if (this.visibleTime < 0) return Visibility.SHOW;
        return (System.currentTimeMillis() - this.visibleTime) >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void update(ToastManager toastManager, long timeSinceLastVisible) {
        if (this.visibleTime < 0) {
            this.visibleTime = System.currentTimeMillis();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, Font font, long timeSinceLastVisible) {
        gui.fill(0, 0, width(), height(), 0xDD1F1F1F);
        gui.outline(0, 0, width(), height(), 0xFFFFCC55);
        if (!icon.isEmpty()) {
            gui.item(icon, 8, 8);
        }
        gui.text(font, title, 30, subTitle == null ? 12 : 7, 0xFFFFCC55, false);
        if (subTitle != null) {
            gui.text(font, subTitle, 30, 19, 0xFFFFFFFF, false);
        }
    }
}
