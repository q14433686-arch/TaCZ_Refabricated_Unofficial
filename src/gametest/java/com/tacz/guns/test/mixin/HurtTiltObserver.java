package com.tacz.guns.test.mixin;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.test.PlayerFeedbackSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=GunHurtBobTweak.class, remap=false)
abstract class HurtTiltObserver {
    @Inject(method="heldGunTilt", at=@At("RETURN"))
    private static void tilt(float angle,float direction,double multiplier,CallbackInfoReturnable<Float> ci) {
        PlayerFeedbackSmoke.tilt(angle,direction,multiplier,ci.getReturnValue());
    }
}
