package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 26.2 interact hint overlay using HudElementRegistry + GuiGraphicsExtractor.
 */
public class InteractKeyTextOverlay {
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (RenderConfig.DISABLE_INTERACT_HUD_TEXT.get()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || !IGun.mainHandHoldGun(player)) return;
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) return;

        boolean canInteract = false;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockState block = player.level().getBlockState(blockHitResult.getBlockPos());
            canInteract = InteractKeyConfigRead.canInteractBlock(block);
        } else if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            canInteract = InteractKeyConfigRead.canInteractEntity(entity);
        }
        if (!canInteract) return;

        Component key = InteractKey.INTERACT_KEY.getTranslatedKeyMessage();
        Component text = Component.translatable("gui.tacz.interact_key.text.desc", key);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 44;
        graphics.centeredText(mc.font, text, x, y, 0xFFFFFFFF);
    }
}
