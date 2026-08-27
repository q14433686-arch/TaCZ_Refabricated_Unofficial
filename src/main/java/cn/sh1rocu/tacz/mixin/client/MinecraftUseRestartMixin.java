package cn.sh1rocu.tacz.mixin.client;

import me.xjqsh.lrtactical.client.input.UsePressGate;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拦住右键长按时由原版自动触发的第二次 startUseItem。 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseRestartMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void lr$blockHeldUseRestart(CallbackInfo ci) {
        if (UsePressGate.shouldBlockRestart()) {
            ci.cancel();
        }
    }
}
