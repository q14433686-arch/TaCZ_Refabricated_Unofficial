package cn.sh1rocu.tacz.mixin.compat.punchy;

import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Punchy's sampled walk bob from leaking into TACZ aiming and authored hand motion.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.HandRenderBobContext", remap = false)
public abstract class PunchyHandRenderBobContextMixin {
    @Inject(method = "updateBobSample", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void tacz$ignoreBobSample(CallbackInfo ci) {
        if (tacz$shouldYield()) {
            ci.cancel();
        }
    }

    @Inject(method = "hasBobSample", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void tacz$hideBobSample(CallbackInfoReturnable<Boolean> cir) {
        if (tacz$shouldYield()) {
            cir.setReturnValue(false);
        }
    }

    private static boolean tacz$shouldYield() {
        return FirstPersonAnimationCompat.shouldUseTaczRenderer(Minecraft.getInstance().player);
    }
}
