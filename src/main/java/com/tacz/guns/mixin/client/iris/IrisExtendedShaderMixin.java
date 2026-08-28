package com.tacz.guns.mixin.client.iris;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * Writes the TACZ scope-mask uniforms when Iris sets up an ExtendedShader program.
 *
 * <p>Iris' {@code MixinGlCommandEncoder} calls {@code iris$setupState} from its own
 * {@code trySetup} RETURN handler, where it does {@code _glUseProgram(getProgramId())}
 * followed by {@code ProgramSamplers#update()} and {@code ProgramUniforms#update()}.
 * That makes this hook the <b>last</b> writer of GL uniform / sampler state for the
 * program that is about to draw, so the scope-mask mode is (re)applied here rather than
 * blindly reset to 0 &mdash; see {@code IrisScopeMaskState#applyToShaderProgram}.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisExtendedShaderMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 0)
    private void tacz$setupScopeMaskUniforms(HashMap<?, ?> samplers, GpuTextureView albedoTex, CallbackInfo ci) {
        IrisScopeMaskState.applyToShaderProgram((Object) this);
    }
}
