package com.tacz.guns.mixin.client.iris;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/** Binds TACZ's per-draw scope mask sampler for patched Iris hand shaders. */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisExtendedShaderMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 0)
    private void tacz$setupScopeMaskUniforms(HashMap<?, ?> samplers, GpuTextureView albedoTex, CallbackInfo ci) {
        IrisScopeMaskState.applyToShaderProgram((Object) this, samplers);
    }
}
