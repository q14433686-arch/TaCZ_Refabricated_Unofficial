package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把每个前端 {@code FrontendRenderPass} 与它的后端 {@code GlRenderPass} 配对登记，
 * 供 {@link IrisScopeMaskState} 在 draw 期按<b>名字</b>查本条 draw 绑定了哪些采样器。
 *
 * <h2>为什么需要它（26.3 实况，2026-09-20 逐字读反编译源）</h2>
 * <p>{@code GlCommandEncoder#setupDraw(GlRenderPass)} 这个 hook 点只能拿到<b>后端</b>
 * pass。26.2 的后端 pass 上有 {@code HashMap<String, GpuTextureView> samplers}，
 * 名字直取即可；26.3 renderpearl 重构后，后端只剩按 uniform <b>下标</b>存放的
 * {@code ReferenceList<Object> uniforms}，「哪个下标对应 ScopeMaskSampler」这层
 * 映射只存在于前端 {@code FrontendRenderPipeline#uniformIndices}，后端对象上
 * 已经没有任何名字信息。而前端 {@code FrontendRenderPass} 保留了
 * {@code HashMap<String, Object> uniforms}（每次 {@code setUniform(name, ...)}
 * 先写它再转发后端），所以只要能从后端找回前端，就能按名字判 mode。</p>
 *
 * <p>前端构造器的第一个实参就是后端 {@code RenderPassBackend}
 * （{@code FrontendRenderPass(backend, device, colorAttachments, hasDepth, onFinish, renderArea)}），
 * 两者一对一、同生同灭。这里在构造 RETURN 处登记一次，
 * {@code IrisScopeMaskState} 侧用弱键表持有，不延长任何对象寿命。</p>
 *
 * <p>与 09-19 被否掉的 {@code FrontendRenderPassPipelineMixin}（挂
 * {@code setPipeline} 登记「前端管线名」）不同：那条路想按<b>管线身份</b>反查，
 * 而 Iris 的重定向会把我们的管线换成它的 HAND 管线、名字随之丢失；本条路登记
 * 的是 <b>pass 与 pass</b> 的对应关系，pass 是 vanilla 自己创建、Iris 不换的，
 * 而按名字的 uniforms 表随 draw 携带、与管线是谁无关。</p>
 *
 * <p>26.2 上不存在 {@code com.mojang.renderpearl.frontend.FrontendRenderPass}
 * 这个类，{@code @Pseudo} + {@code require = 0} 让本 mixin 在那条分支上静默不装；
 * 26.2 的 {@code samplersMap} 老路不受影响。</p>
 */
@Pseudo
@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPass")
public abstract class IrisFrontendRenderPassMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void tacz$noteFrontendPass(CallbackInfo ci) {
        IrisScopeMaskState.noteFrontendPass(this);
    }
}
