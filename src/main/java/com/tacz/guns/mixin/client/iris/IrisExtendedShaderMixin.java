package com.tacz.guns.mixin.client.iris;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * Zeros the scope mask uniform when Iris initializes an ExtendedShader program.
 *
 * <p>This prevents a stale {@code tacz_ScopeMaskMode} value from a previous frame
 * or a different render pass from causing random pixel discarding.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisExtendedShaderMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 0)
    private void tacz$setupScopeMaskUniforms(HashMap<?, ?> samplers, GpuTextureView albedoTex, CallbackInfo ci) {
        IrisScopeMaskState.resetShaderProgram(this);
    }
}
