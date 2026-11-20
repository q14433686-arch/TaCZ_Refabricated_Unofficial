package com.tacz.guns.mixin.client.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.CompareOp;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import com.tacz.guns.compat.iris.IrisThermalDepthBias;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Writes the TACZ scope-mask mode / sampler on every render pass draw setup in GlCommandEncoder.
 *
 * <p>Two hooks, deliberately:</p>
 * <ul>
 *   <li><b>HEAD</b> &mdash; records the pass. Iris' own {@code MixinGlCommandEncoder} also
 *       injects at {@code trySetup} RETURN and calls {@code ExtendedShader#iris$setupState}
 *       there, which re-binds the program and all of its samplers. HEAD always runs before
 *       any RETURN handler, so the pass is known by the time either writer needs it.</li>
 *   <li><b>RETURN</b> &mdash; applies the state. Together with
 *       {@code IrisExtendedShaderMixin} (which applies it at the end of
 *       {@code iris$setupState}) the correct value is written no matter which of the two
 *       RETURN handlers the mixin application order happens to run last.</li>
 * </ul>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisGlCommandEncoderMixin {
    // Heat replay is deliberately outside Iris's world phase so it doesn't redirect
    // the FBO or expand entity vertices. Only its copied depth retains the world convention.
    @ModifyExpressionValue(method = "applyPipelineState", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/DepthStencilState;depthTest()Lcom/mojang/blaze3d/platform/CompareOp;"))
    private CompareOp tacz$sensorDepth(CompareOp compare) {
        if (!IrisThermalDepthBias.replayingForwardDepth()) return compare;
        return switch (compare) {
            case GREATER_THAN_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
            case GREATER_THAN -> CompareOp.LESS_THAN;
            case LESS_THAN_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
            case LESS_THAN -> CompareOp.GREATER_THAN;
            default -> compare;
        };
    }

    @Inject(method = "trySetup", at = @At("HEAD"), require = 0)
    private void tacz$captureScopeRenderPass(@Coerce Object glRenderPass,
                                             Collection<String> missingResources,
                                             CallbackInfoReturnable<Boolean> cir) {
        IrisScopeMaskState.setCurrentPass(glRenderPass);
    }

    @Inject(method = "trySetup", at = @At("RETURN"), require = 0)
    private void tacz$onScopeRenderPassSetup(@Coerce Object glRenderPass,
                                             Collection<String> missingResources,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            IrisScopeMaskState.applyToGlRenderPass(glRenderPass);
            IrisThermalDepthBias.apply();
        }
    }
}
