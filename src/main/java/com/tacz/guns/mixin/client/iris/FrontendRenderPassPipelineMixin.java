package com.tacz.guns.mixin.client.iris;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在前端登记「后端管线对象 → 管线 location 名」的映射。
 *
 * <h2>为什么必须在这一层抓</h2>
 * <p>{@code IrisScopeMaskState#resolveMode} 需要知道「当前这次 draw 属于哪条 TACZ 管线」，
 * 才能决定 {@code tacz_ScopeMaskMode} 写 1（镜身）还是 2（准星/文字）。
 * 它手里只有 Iris 递过来的<b>后端</b> {@code GlRenderPass}。</p>
 *
 * <p>26.2 时可以从后端一路反查回前端：{@code GlRenderPipeline#info()} →
 * {@code RenderPipeline#getLocation()}。26.3 砍掉了 {@code info()}，
 * 后端管线只剩纯 GL 状态。上一轮改读 {@code program().getDebugLabel()}，
 * 探针证明这条路同样不成立 —— 实机回报
 * {@code firstTaczPipeline=null, firstAnyPipelineLabel=sky_basic}。
 * {@code sky_basic} 是 <b>Iris 着色器包自己的程序名</b>：光影激活时 Iris 会把
 * pack 的程序换进管线（我们的 scope 管线被 assignPipeline 映射到 HAND_CUTOUT），
 * 于是后端拿到的 debugLabel 永远是 Iris 的程序名，而不是我们的 location ——
 * 无论怎么在后端翻找，都不可能翻出 {@code tacz:pipeline/xxx}。</p>
 *
 * <h2>做法</h2>
 * <p>{@code FrontendRenderPass#setPipeline} 是「前端管线 → 后端管线」的<b>唯一</b>交接点
 * （FrontendRenderPass:104-124）：它拿着 {@code FrontendRenderPipeline}，
 * 其 {@code name()} 就是 {@code pipeline.getLocation().toString()}
 * （PipelineBuilder:346-347 写入），然后把 {@code backendRenderPipeline()}
 * 交给后端 —— 那个对象正是后续 {@code GlRenderPass.pipeline} 字段里存的东西
 * （GlRenderPass:57-60）。在这里把两者配对记下来，后端侧就能按对象身份反查名字。</p>
 *
 * <p>这一层是 vanilla 的 renderpearl，不是 Iris 内部实现，所以无论 Iris 之后
 * 怎么替换程序都不影响登记结果；不装 Iris 时这条映射也照样正确（只是用不上）。</p>
 */
@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPass", remap = false)
public abstract class FrontendRenderPassPipelineMixin {
    @Inject(method = "setPipeline", at = @At("HEAD"), require = 0)
    private void tacz$recordPipelineName(@Coerce Object pipeline, CallbackInfo ci) {
        IrisScopeMaskState.notePipelineBinding(pipeline);
    }
}
