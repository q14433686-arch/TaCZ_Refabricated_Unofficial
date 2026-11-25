package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeFragment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Adds depth-restore and ocular screen-space mask branches to Iris hand fragment shaders.
 *
 * <p>Important NVIDIA compatibility note: older revisions injected these dormant branches into
 * every Iris shader-pack program. Even with both mode uniforms at 0, merely adding conditional
 * {@code discard} / {@code gl_FragDepth} paths can make some NVIDIA drivers compile ordinary
 * gbuffers programs differently, producing translucent-looking mobs, arms and gun shells. The
 * scope depth backup is only consumed by first-person hand draws, so only hand keys get
 * depth restore. One emissive key gets an outside-aperture discard, never a depth write.</p>
 *
 * <p>Under Iris the mask world-depth source is {@code depthtex2}, which Iris copies immediately
 * before HAND_SOLID, while the aperture depth is the mod-owned copy bound to a high texture unit
 * for the duration of the reticle draw.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisDepthRestoreShaderMixin {
    @ModifyArgs(
            method = "link",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;createShader(Ljava/lang/String;Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;)I",
                    ordinal = 4
            ),
            require = 0
    )
    private static void tacz$injectScopeBranchesIntoFragmentShader(Args args) {
        String name = (String) args.get(0);
        String source = (String) args.get(2);
        args.set(2, IrisScopeFragment.patch(name, source));
    }
}
