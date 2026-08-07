package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Updates TACZ scope-mask uniform mode and texture unit on every render pass draw setup in GlCommandEncoder. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisGlCommandEncoderMixin {
    @Inject(method = "trySetup", at = @At("RETURN"), require = 0)
    private void tacz$onScopeRenderPassSetup(@Coerce Object glRenderPass,
                                             Collection<String> missingResources,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            IrisScopeMaskState.applyToGlRenderPass(glRenderPass);
        }
    }
}
