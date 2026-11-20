package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.block.GunDisplayCaseBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Feedback for a use *press*, not a prompt continuously refreshed by the crosshair. */
public final class DisplayCaseLockHint {
    private static final int DISPLAY_TICKS = 40;
    private static ClientLevel level;
    private static Component message;
    private static int remaining;

    private DisplayCaseLockHint() {}

    public static void onEmptyHandUse(Minecraft mc) {
        var player = mc.player;
        if (player == null || player.isSpectator() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var state = player.level().getBlockState(hit.getBlockPos());
        if (state.getBlock() instanceof GunDisplayCaseBlock) show(mc, state.getValue(GunDisplayCaseBlock.LOCKED));
    }

    public static void show(Minecraft mc, boolean locked) {
        var chord = mc.options.keyShift.getTranslatedKeyMessage().copy().append(" + ").append(mc.options.keyUse.getTranslatedKeyMessage());
        message = Component.translatable(locked ? "gui.tacz.display_case.locked_hint" : "gui.tacz.display_case.unlocked_hint", chord);
        level = mc.level;
        remaining = DISPLAY_TICKS;
    }

    public static void tick(Minecraft mc) {
        if (remaining == 0) return;
        if (--remaining == 0 || mc.level != level || mc.player == null) {
            remaining = 0;
            message = null;
            level = null;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (remaining == 0) return;
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.options.hideGui) return;
        graphics.drawString(mc.font, message, (graphics.guiWidth() - mc.font.width(message)) / 2,
                graphics.guiHeight() / 2 + 32, 0xff000000 | ChatFormatting.YELLOW.getColor(), false);
    }
}
