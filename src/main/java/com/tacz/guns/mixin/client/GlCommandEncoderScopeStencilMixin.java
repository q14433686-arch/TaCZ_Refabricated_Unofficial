package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.render.scope.ScopeStencilState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies scope stencil state at the real GPU scheduling boundary.
 *
 * <p>{@code GlCommandEncoder#createRenderPass} has selected the destination FBO and
 * {@code trySetup} (including Iris' replacement program setup) has completed before
 * {@code drawFromBuffers} is entered. This is therefore the first backend point where both the correct
 * framebuffer and final pipeline are guaranteed to be active.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderScopeStencilMixin {
    @Inject(method = "drawFromBuffers", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$prepareScopeStencilDraw(@Coerce Object glRenderPass,
                                              int baseVertex,
                                              int firstIndex,
                                              int indexCount,
                                              VertexFormat.IndexType indexType,
                                              @Coerce Object glRenderPipeline,
                                              int instanceCount,
                                              CallbackInfo ci) {
        if (!ScopeStencilState.prepareCurrentDraw()) {
            ci.cancel();
        }
    }
}
