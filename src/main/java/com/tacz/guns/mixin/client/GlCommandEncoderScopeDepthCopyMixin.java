package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.render.scope.ScopeMaskState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures scope mask uniforms are zeroed for non-scope draws in the vanilla rendering path.
 *
 * <p>The Iris path is handled separately by {@code IrisGlCommandEncoderMixin} which hooks
 * into {@code trySetup} and uses pipeline inspection. This mixin handles the vanilla path
 * where the custom scope_body shader has the mask logic built into its source.</p>
 *
 * <h2>Why zero on every draw?</h2>
 * The {@code tacz_ScopeMaskMode} uniform is compiled into patched shaders.
 * If a non-scope draw (gun body, hands, particles) has a stale non-zero value,
 * pixels would be randomly discarded. Explicit zeroing prevents this.</p>
 *
 * <h2>Iris interaction</h2>
 * When Iris is loaded, it also hooks into {@code drawFromBuffers} for depth management.
 * Both hooks coexist: Iris handles its shader replacement path, this handles vanilla.
 * The {@code require = 1} ensures this mixin fails gracefully if the method signature changes.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderScopeDepthCopyMixin {
    @Inject(method = "drawFromBuffers", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$ensureScopeMaskZeroed(@Coerce Object glRenderPass,
                                            int baseVertex,
                                            int firstIndex,
                                            int indexCount,
                                            VertexFormat.IndexType indexType,
                                            @Coerce Object glRenderPipeline,
                                            int instanceCount,
                                            CallbackInfo ci) {
        // Always zero the scope mask uniform to prevent stale state leakage.
        // The actual mask uniform is set by:
        // - Vanilla path: ScopeBodyRenderTypes.MaskAwareRenderType.draw() before each scope draw
        // - Iris path: IrisScopeMaskState.applyToGlRenderPass() via IrisGlCommandEncoderMixin
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program > 0) {
            int modeLoc = GL20.glGetUniformLocation(program, "tacz_ScopeMaskMode");
            if (modeLoc >= 0) {
                // Don't zero if a mask draw is active (set by MaskAwareRenderType)
                if (!ScopeMaskState.isMaskActive()) {
                    GL20.glUniform1i(modeLoc, 0);
                }
            }
        }
    }
}
