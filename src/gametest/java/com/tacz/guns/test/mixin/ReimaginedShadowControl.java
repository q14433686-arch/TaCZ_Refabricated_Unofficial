package com.tacz.guns.test.mixin;

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
        else if (ReimaginedSmoke.oldMaterial != null && body == ReimaginedSmoke.svdBody) ci.setReturnValue(ReimaginedSmoke.oldMaterial);
    }
}
