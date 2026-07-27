package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * 26.2 heat bar implementation using GuiGraphicsExtractor primitives.
 */
public class HeatBarOverlay {
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun) || !iGun.hasHeatData(stack)) return;

        TimelessAPI.getClientGunIndex(iGun.getGunId(stack))
                .map(ClientGunIndex::getGunData)
                .filter(data -> data.getHeatData() != null)
                .ifPresent(data -> {
                    float percent = Mth.clamp(iGun.getHeatAmount(stack) / Math.max(1.0f, data.getHeatData().getHeatMax()), 0.0f, 1.0f);
                    if (percent <= 0.0f && !iGun.isOverheatLocked(stack)) return;

                    int width = graphics.guiWidth();
                    int height = graphics.guiHeight();
                    int barWidth = 104;
                    int barHeight = 5;
                    int x = width - barWidth - 12;
                    int y = height - 12 - 38 - 8;
                    graphics.fill(x, y, x + barWidth, y + barHeight, 0x66000000);
                    graphics.fill(x, y, x + Math.round(barWidth * percent), y + barHeight,
                            getHeatColor(percent, iGun.isOverheatLocked(stack), mc.player.tickCount));
                    graphics.outline(x, y, barWidth, barHeight, 0x99FFFFFF);
                });
    }

    public static int getHeatColor(float percent, boolean locked, int tickCount) {
        if (locked) {
            return tickCount % 20 < 10 ? 0x9FFF0000 : 0x9FFFFF00;
        }
        if (percent < 0.4) return 0x9FFFFFFF;
        int color;
        if (percent <= 0.65) {
            color = ARGB.srgbLerp(percent * 4 - 1.6f, 0x9FFFFFFF, 0x9FFFFF00);
        } else {
            color = ARGB.srgbLerp((percent - 0.65f) / 0.35f, 0x9FFFFF00, 0x9FFF0000);
        }
        return color;
    }
}
