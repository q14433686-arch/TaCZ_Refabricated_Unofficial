package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the scope depth backup, ocular aperture copy, world-depth restore and reticle mask binding
 * after vanilla/Iris bind the real destination FBO and before glDraw*.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderScopeDepthCopyMixin {
    @Inject(method = "drawFromBuffers", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$copyScopeDepth(@Coerce Object glRenderPass,
                                     int baseVertex,
                                     int firstIndex,
                                     int indexCount,
                                     VertexFormat.IndexType indexType,
                                     @Coerce Object glRenderPipeline,
                                     int instanceCount,
                                     CallbackInfo ci) {
        if (!ScopeDepthCopyState.beforeDraw()) {
            ci.cancel();
        }
    }
}
