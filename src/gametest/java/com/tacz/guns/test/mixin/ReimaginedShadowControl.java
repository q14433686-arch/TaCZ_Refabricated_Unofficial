package com.tacz.guns.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.client.model.bedrock.IlluminatedMaterials;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.test.ReimaginedSmoke;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=IlluminatedMaterials.class,remap=false)
abstract class ReimaginedShadowControl {
    @Inject(method="forBody",at=@At("HEAD"),cancellable=true)
    private static void reference(RenderType body, boolean hand, CallbackInfoReturnable<RenderType> ci) {
        if (!IrisCompat.isRenderShadow()) return;
        if (ReimaginedSmoke.shadowReference) { ReimaginedSmoke.shadowBodies++; ci.setReturnValue(body); }
    }

    // Disable only the new shadow-material branch, for exactly this invocation. Do not
    // substitute a guessed SVD texture or lie to Iris about which GPU pass is running.
    @ModifyExpressionValue(method="forBody", at=@At(value="INVOKE",target="Lcom/tacz/guns/compat/iris/IrisCompat;isRenderShadow()Z"))
    private static boolean oldDecision(boolean shadow) { return shadow && !ReimaginedSmoke.forceOld; }

    @Inject(method="forBody",at=@At("RETURN"))
    private static void countOld(RenderType body, boolean hand, CallbackInfoReturnable<RenderType> ci) {
        if (ReimaginedSmoke.forceOld && !hand && IrisCompat.isRenderShadow() && ci.getReturnValue()!=body) ReimaginedSmoke.oldSelections++;
    }
}
