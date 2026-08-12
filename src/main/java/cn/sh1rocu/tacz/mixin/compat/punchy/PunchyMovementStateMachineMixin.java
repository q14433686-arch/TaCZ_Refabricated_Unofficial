package cn.sh1rocu.tacz.mixin.compat.punchy;

import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes Punchy's walk/sprint/camera-lag matrices while TACZ owns the viewmodel. */
@Pseudo
@Mixin(targets = "punchy.client.state.MovementStateMachine", remap = false)
public abstract class PunchyMovementStateMachineMixin {
    @Inject(method = {
            "applyToMatrix",
            "applyRootSpaceMotion",
            "applyCameraLookToRoot",
            "applyCameraYawLagToArm"
    }, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tacz$skipMovementMatrices(CallbackInfo ci) {
        if (tacz$shouldYield()) {
            ci.cancel();
        }
    }

    @Inject(method = {"isSprintSwingActive", "isWalkSwingActive"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tacz$disableMovementSwing(CallbackInfoReturnable<Boolean> cir) {
        if (tacz$shouldYield()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getSprintBlendAlpha", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void tacz$clearSprintBlend(CallbackInfoReturnable<Float> cir) {
        if (tacz$shouldYield()) {
            cir.setReturnValue(0.0F);
        }
    }

    private static boolean tacz$shouldYield() {
        return FirstPersonAnimationCompat.shouldUseTaczRenderer(Minecraft.getInstance().player);
    }
}
