package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopeShaderInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the dormant depth-restore and ocular screen-space mask branches to the Iris <b>hand</b>
 * fragment programs, and to nothing else.
 *
 * <p>Under a shader pack Iris replaces TACZ's {@code scope_*} pipelines with the pack's
 * {@code gbuffers_hand} / {@code gbuffers_hand_water} program, so the mod cannot ship its own
 * fragment shader for those draws; the branches have to live inside the pack's program. Both stay
 * inert while {@code tacz_DepthRestoreMode} and {@code tacz_ScopeMaskMode} are 0, and
 * {@code ScopeDepthCopyState} enables exactly one of them around the cleanup or reticle draw.
 *
 * <p>The previous revision of this mixin patched <i>every</i> program Iris links, which turned all
 * world, entity, particle and shadow shaders into {@code gl_FragDepth}-exporting shaders that never
 * assign it on the path they actually take — undefined depth per the GLSL spec, and therefore a
 * different failure on every driver. See {@link ScopeShaderInjector} for the full analysis; the
 * program-name filter lives there.
 *
 * <p>{@code link}'s parameter list is
 * {@code (String name, String vertex, String geometry, String tessControl, String tessEval,
 * String fragment, VertexFormat vertexFormat, boolean isFallback)} and the method is static, so
 * local index 5 is the fragment source.
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisScopeHandShaderMixin {
    @ModifyVariable(
            method = "link",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5,
            require = 0
    )
    private static String tacz$injectScopeBranches(String fragment) {
        return ScopeShaderInjector.patchLinkedFragment(fragment);
    }
}
