package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Applies scope mask uniform state on every Iris render pass draw setup.
 *
 * <p>This mixin targets {@code GlCommandEncoder#trySetup} which is called by Iris
 * when setting up each render pass. It inspects the pipeline to determine if
 * the current draw is a scope body or reticle pass, and sets the appropriate
 * {@code tacz_ScopeMaskMode} uniform.</p>
 *
 * <p>For non-scope draws, it explicitly zeros the uniform to prevent stale
 * state from causing random clipping on unrelated geometry.</p>
 *
 * <h2>Why trySetup and not drawFromBuffers</h2>
 * Iris calls {@code trySetup} to set up the render pass state (pipeline, textures, etc.)
 * before the first draw. This is the correct place to inject our uniforms because:
 * <ul>
 *   <li>The shader program is already bound (set by Iris' pipeline setup)</li>
 *   <li>It runs once per render pass, not once per draw (more efficient)</li>
 *   <li>It has access to the pipeline info for mode detection</li>
 * </ul>
 */
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
