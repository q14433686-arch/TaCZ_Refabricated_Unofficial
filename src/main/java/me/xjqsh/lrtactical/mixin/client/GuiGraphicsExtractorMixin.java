package me.xjqsh.lrtactical.mixin.client;

import me.xjqsh.lrtactical.api.item.ICustomItem;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds LRTactical's id-keyed cooldown mask beside vanilla item cooldown rendering. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Inject(method = "itemCooldown(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("TAIL"))
    private void lrtactical$customCooldown(ItemStack stack, int x, int y, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(stack.getItem() instanceof ICustomItem item)) {
            return;
        }
        item.getCoolDownId(stack).ifPresent(id -> {
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float progress = ModCapabilities.coolDowns(minecraft.player)
                    .getCooldownPercent(id, partialTick);
            if (progress <= 0.0F) {
                return;
            }
            int top = y + Mth.floor(16.0F * (1.0F - progress));
            int bottom = top + Mth.ceil(16.0F * progress);
            ((GuiGraphicsExtractor) (Object) this).fill(
                    RenderPipelines.GUI, x, top, x + 16, bottom, Integer.MAX_VALUE);
        });
    }
}
