package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    // 【26.3】Iris 把 iris$setupState 的形参从 (HashMap, GpuTextureView) 改成了
    // 单参 List<BindGroupLayout.UniformDescription>（Iris 26.3 ExtendedShader:208）。
    // 旧签名导致 mixin 在 APPLY 阶段抛 InvalidInjectionException，整个
    // IrisExtendedShaderMixin 被丢弃 —— 这正是「光影下开镜裁剪与二次渲染全失效」
    // 的直接原因（2026-09-18 01:49 日志 line 189）：裁剪在光影下本就不靠
    // scope_body.fsh（Iris 会整条替换掉我们的着色器），而是靠这里写入的
    // tacz_ScopeMaskMode uniform，hook 没装上 = 模式永远是 0 = 不裁剪。
    //
    // 这里改用 mixin 的「空形参」注入形式（只声明 CallbackInfo）：本 hook 只需要
    // 「Iris 刚把 program 的 uniform/sampler 全部更新完」这个时机，一个形参都不读，
    // 空形参形式对 Iris 后续再改签名天然免疫。require = 0 保持不变 —— 未装 Iris
    // 或 Iris 内部重构导致方法改名时，静默跳过而不是崩游戏。
    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 0)
    private void tacz$setupScopeMaskUniforms(CallbackInfo ci) {
        IrisScopeMaskState.applyToShaderProgram((Object) this);
    }
}
