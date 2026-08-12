package me.xjqsh.lrtactical.client.overlay;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.ICustomItem;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** 26.2 extracted-HUD port of LRTactical's use/cook/melee progress feedback. */
@Environment(EnvType.CLIENT)
public final class UsingProgressOverlay {
    private UsingProgressOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        int x = graphics.guiWidth() / 2 - 16;
        int y = graphics.guiHeight() / 2 + 16;
        ItemStack stack = player.getUseItem();
        if (!stack.isEmpty() && stack.getItem() instanceof ICustomItem customItem) {
            int maxTicks = customItem.getMaxUsingTick(stack);
            int usingTicks = player.getTicksUsingItem();
            if (maxTicks > 0) {
                float progress = Mth.clamp(usingTicks / (float) maxTicks, 0.0F, 1.0F);
                int alpha = progress >= 1.0F
                        ? Mth.clamp((int) (80 + 80 * Math.sin(usingTicks / 2.0F)), 1, 160)
                        : 0x80;
                boolean toggle = stack.getItem() instanceof IConsumable
                        && LrTacticalAPI.getConsumableIndex(stack)
                        .map(index -> index.getData().isToggleUse()).orElse(false);
                int color = (alpha << 24) | (toggle ? 0x00FF00 : 0xFFFFFF);
                graphics.fill(x, y, x + Math.round(progress * 32), y + 4, color);

                if (stack.getItem() instanceof IThrowable throwable) {
                    throwable.getThrowableIndex(stack).ifPresent(index -> {
                        var data = index.getData();
                        int life = data.getEntityData().getLifeTime();
                        int maxCookTicks = Math.max(1, (int) (life * 0.9F));
                        if (data.isCookable() && life > 0 && usingTicks >= data.getPrepareTime()) {
                            // ThrowableItem detonates in-hand at 90% of life; use that same
                            // denominator so the warning bar actually reaches full.
                            float cooked = Mth.clamp(
                                    (usingTicks - data.getPrepareTime()) / (float) maxCookTicks,
                                    0.0F, 1.0F);
                            graphics.fill(x, y, x + Math.round(cooked * 32), y + 4,
                                    (alpha << 24) | 0xFF0000);
                        }
                    });
                    if (player.isCrouching()) {
                        // The upstream arrow texture is ARR and is not distributed. Draw the same
                        // four-pixel cue geometrically through the 26.2 GUI pipeline.
                        graphics.fill(x + 12, y + 6, x + 20, y + 7, 0xB3FFFFFF);
                        graphics.fill(x + 13, y + 7, x + 19, y + 8, 0xB3FFFFFF);
                        graphics.fill(x + 14, y + 8, x + 18, y + 9, 0xB3FFFFFF);
                        graphics.fill(x + 15, y + 9, x + 17, y + 10, 0xB3FFFFFF);
                    }
                }

                if (toggle) {
                    Component hint = Component.translatable("overlay.lrtactical.consumable.toggle_hint");
                    graphics.text(minecraft.font, hint,
                            graphics.guiWidth() / 2 - minecraft.font.width(hint) / 2,
                            y + 8, 0xFFFFFFFF, true);
                }
            }
        }

        var combat = ModCapabilities.combatProperties(player);
        if (combat.getCoolDownTick() > 0 && combat.getLastMaxTick() > 0) {
            float progress = 1.0F - Mth.clamp(
                    combat.getCoolDownTick() / (float) combat.getLastMaxTick(), 0.0F, 1.0F);
            graphics.fill(x, y, x + Math.round(progress * 32), y + 4, 0x80FFFFFF);
        }
    }
}
