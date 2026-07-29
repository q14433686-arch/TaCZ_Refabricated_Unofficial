package cn.sh1rocu.tacz.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.PreventsHotbarEvent;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import com.tacz.guns.compat.immediatelyfast.ImmediatelyFastCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void tacz$renderHotbarItemPre(GuiGraphicsExtractor context, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        ImmediatelyFastCompat.renderHotbarItem(stack, true);
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void tacz$renderHotbarItemPost(GuiGraphicsExtractor context, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        ImmediatelyFastCompat.renderHotbarItem(stack, false);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void tacz$onRender(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        PreventsHotbarEvent.onRenderHotbarEvent(cancelled);
        if (cancelled.get()) {
            ci.cancel();
        }
    }

    // 需要渲染枪械准心时取消原版渲染
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void tacz$renderCrosshair(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return;
        }

        RenderCrosshairEvent.onRenderOverlay(context, Minecraft.getInstance().getWindow());

        ci.cancel();
    }
}
