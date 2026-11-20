package com.tacz.guns.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.image.BufferedImage;
import java.util.BitSet;

/** Use the rack smoke's real, initially locked survival cabinet and read the HUD from the framebuffer. */
final class DisplayCaseHintSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos) {
        var oldUse = ctx.computeOnClient(mc -> KeyMappingHelper.getBoundKeyOf(mc.options.keyUse));
        boolean disabled = ctx.computeOnClient(mc -> RenderConfig.DISABLE_INTERACT_HUD_TEXT.get());
        var input = ctx.getInput();
        try {
            ctx.runOnClient(mc -> {
                RenderConfig.DISABLE_INTERACT_HUD_TEXT.set(false);
                mc.options.keyUse.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT));
                KeyMapping.resetMapping();
            });
            aim(ctx, pos);
            frame(ctx, "gun_hover", 0);
            int mouseWidth = width(ctx, true);
            input.pressKey(InteractKey.INTERACT_KEY);
            var lockedMouse = frame(ctx, "gun_interact", mouseWidth);
            RackPreviewSmoke.equip(world, ItemStack.EMPTY);
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            ctx.waitTicks(40);
            frame(ctx, "empty_hover", 0);
            ctx.runOnClient(mc -> RenderConfig.DISABLE_INTERACT_HUD_TEXT.set(true));
            input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            input.lookAt(180, -60);
            ctx.waitFor(mc -> mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS);
            ctx.runOnClient(mc -> {
                mc.options.keyUse.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_J));
                KeyMapping.resetMapping();
            });
            if (!lockedMouse.equals(frame(ctx, "look_away", mouseWidth)))
                throw new AssertionError("Looking away or rebinding Use changed the active message");

            aim(ctx, pos);
            int keyWidth = width(ctx, true);
            // Refresh before expiry. TestInput.holdKey/releaseKey dispatch physical edges without ticking.
            input.holdKey(o -> o.keyUse);
            input.releaseKey(o -> o.keyUse);
            ctx.waitTicks(39);
            var lockedKey = frame(ctx, "tick_39", keyWidth);
            input.holdKey(o -> o.keyUse);
            ctx.waitTicks(40);
            frame(ctx, "tick_40_held", 0);
            input.releaseKey(o -> o.keyUse);

            input.holdShift();
            ctx.waitFor(mc -> mc.player.isShiftKeyDown());
            ClientProbe.waitForShift(ctx, world, true);
            input.pressKey(o -> o.keyUse);
            ctx.waitFor(mc -> !mc.level.getBlockState(pos).getValue(GunDisplayCaseBlock.LOCKED));
            frame(ctx, "sneak_unlock", 0);
            input.releaseShift();
            ctx.waitFor(mc -> !mc.player.isShiftKeyDown());
            aim(ctx, pos);
            input.pressKey(o -> o.keyUse);
            if (lockedKey.equals(frame(ctx, "unlocked", width(ctx, false))))
                throw new AssertionError("Unlocked cabinet showed the locked text");
            ctx.waitTicks(40);
            GunMod.LOGGER.info("PORT_CASE_HINT: gun Interact, mouse Use, rebound Use, look-away, tick 39/40 held, crouch unlock and unlocked text passed");
        } finally {
            input.releaseKey(o -> o.keyUse);
            input.releaseShift();
            ctx.runOnClient(mc -> {
                mc.options.keyUse.setKey(oldUse);
                KeyMapping.resetMapping();
                RenderConfig.DISABLE_INTERACT_HUD_TEXT.set(disabled);
            });
        }
    }

    private static void aim(ClientGameTestContext ctx, BlockPos pos) {
        ctx.getInput().lookAt(pos);
        ctx.waitFor(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos));
    }

    private static int width(ClientGameTestContext ctx, boolean locked) {
        return ctx.computeOnClient(mc -> mc.font.width(Component.translatable(
                locked ? "gui.tacz.display_case.locked_hint" : "gui.tacz.display_case.unlocked_hint",
                mc.options.keyShift.getTranslatedKeyMessage().getString() + " + " + mc.options.keyUse.getTranslatedKeyMessage().getString())));
    }

    private static BitSet frame(ClientGameTestContext ctx, String name, int lowerWidth) {
        var image = ClientProbe.read(ctx.takeScreenshot("tacz_case_hint_" + name));
        checkBand(ctx, image, name, -25, 0);
        return checkBand(ctx, image, name, 32, lowerWidth);
    }

    static BitSet checkBand(ClientGameTestContext ctx, BufferedImage image, String name, int offset, int textWidth) {
        var scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        int w = image.getWidth(), h = image.getHeight(), guiW = Math.ceilDiv(w, scale), guiH = Math.ceilDiv(h, scale);
        int y = (guiH / 2 + offset) * scale, left = w, right = -1;
        BitSet ink = new BitSet();
        for (int dy = 0; dy < 9 * scale; dy++) for (int x = 0; x < w; x++) {
            if (image.getRGB(x, y + dy) != 0xffffff55) continue;
            ink.set(dy * w + x); left = Math.min(left, x); right = Math.max(right, x);
        }
        int start = (guiW - textWidth) / 2 * scale;
        if (textWidth == 0 ? !ink.isEmpty() : ink.cardinality() < 20 || Math.abs(left - start) > 2 * scale
                || Math.abs(right + 1 - (start + textWidth * scale)) > 2 * scale)
            throw new AssertionError(name + " at HUD offset " + offset + ": yellow bounds " + left + ".." + right
                    + ", expected x=" + start + ", width=" + textWidth * scale + ", pixels=" + ink.cardinality());
        return ink;
    }
}
