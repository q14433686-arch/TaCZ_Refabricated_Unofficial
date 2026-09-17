package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Writes the TACZ scope-mask mode / sampler on every render pass draw setup in GlCommandEncoder.
 *
 * <p>Two hooks, deliberately:</p>
 * <ul>
 *   <li><b>HEAD</b> &mdash; records the pass. Iris' own {@code MixinGlCommandEncoder} also
 *       injects at {@code setupDraw} RETURN and calls {@code ExtendedShader#iris$setupState}
 *       there, which re-binds the program and all of its samplers. HEAD always runs before
 *       any RETURN handler, so the pass is known by the time either writer needs it.</li>
 *   <li><b>RETURN</b> &mdash; applies the state. Together with
 *       {@code IrisExtendedShaderMixin} (which applies it at the end of
 *       {@code iris$setupState}) the correct value is written no matter which of the two
 *       RETURN handlers the mixin application order happens to run last.</li>
 * </ul>
 *
 * <h2>26.3 变更</h2>
 * <p>目标方法由 {@code boolean trySetup(GlRenderPass, Collection<String>)} 变成
 * {@code void setupDraw(GlRenderPass)}（vanilla GlCommandEncoder:527；Iris 26.3 的
 * MixinGlCommandEncoder 也已改注入 {@code setupDraw}）。
 * 因为两个 hook 都写了 {@code require = 0}，方法改名后它们只是<b>静默不装</b>，
 * 既不报错也不生效 —— 这是「光影下开镜裁剪失效」的第二个原因，
 * 比 IrisExtendedShaderMixin 那个显式报错的更隐蔽。
 *
 * <p>返回值也没了：原先 RETURN hook 靠 {@code cir.getReturnValue()} 判断
 * 「setup 成功才应用状态」。{@code setupDraw} 返回 void，改为无条件应用 ——
 * 语义上等价于旧的 {@code true} 分支，因为 Iris 的 bypass 路径会在
 * {@code iris$bypassSetup} 里直接 {@code cir.cancel()}，被取消时我们的 RETURN
 * hook 同样不会执行。</p>
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class IrisGlCommandEncoderMixin {
    @Inject(method = "setupDraw", at = @At("HEAD"), require = 0)
    private void tacz$captureScopeRenderPass(@Coerce Object glRenderPass, CallbackInfo ci) {
        IrisScopeMaskState.setCurrentPass(glRenderPass);
    }

    @Inject(method = "setupDraw", at = @At("RETURN"), require = 0)
    private void tacz$onScopeRenderPassSetup(@Coerce Object glRenderPass, CallbackInfo ci) {
        IrisScopeMaskState.applyToGlRenderPass(glRenderPass);
    }
}
