package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the scope PIP lens and then the frozen reticle/rim geometry after Iris has run all
 * composite and final passes.
 *
 * <p>{@code IrisRenderingPipeline#finalizeLevelRendering()} first sets {@code isRenderingWorld}
 * false and then runs composite/final programs. At TAIL Iris no longer replaces core pipelines;
 * both {@link ScopePipRenderState#captureSceneAfterIrisFinal(Minecraft)} and
 * {@link ScopeFinalOverlayState} can therefore work on the main output while retaining the hand
 * projection captured during {@code HAND_SOLID}.</p>
 *
 * <p>Order is deliberately: finished shader frame -&gt; magnified PIP lens -&gt; reticle/crosshair
 * -&gt; ocular shade. When {@code ScopePipAllowShaderPacks} is off the PIP methods are no-ops and
 * this behaves exactly as before.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisFinalScopeOverlayMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), require = 1)
    private void tacz$drawScopeAfterShaderPackFinal(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ScopePipRerender.isInsideScopeLevelRender()) {
            // 二次渲染的窄遍同样会跑到这里：finalize 在每一遍 renderLevel 内部都会执行，光影下
            // 一帧共有两次。窄遍里的合成与延迟覆盖层是纯粹的污染源 —— 画上主目标只会被宽遍整体
            // 重画覆盖，而紧随其后的 renderScopeView 捕获却会把「上一帧的镜内画面 + 遮光罩」一起
            // 拷进新镜内，合成结果回灌自身（实机表现：镜内容冻结在第一帧、遮光罩随移动逐帧复制
            // 粘贴，2026-09-01）。窄遍只负责把窄 FOV 世界画干净；拷贝由 renderScopeView 在
            // renderLevel 返回后做，合成与遮光罩留给宽遍自己的 finalize（那时 sceneCaptured 已就绪、
            // scopePassActive 已复位）。
            // 【这条为什么在本线是必须的】本线是 2026-09-01 那批"放行光影窄遍"的改动才让这条
            // 双触发路径第一次跑起来的；放行与这两道守卫（同批还有 captureSceneAfterIrisFinal）
            // 是一个整体，漏一条的后果不是"少个优化"而是镜内画面被 1x 主画面顶替（实机：镜内外均 1X）。
            ScopeFinalOverlayState.discardPendingOverlays();
            return;
        }
        ScopePipRenderState.captureSceneAfterIrisFinal(minecraft);
        ScopePipRenderState.compositeAfterIrisFinal(minecraft);
        ScopeFinalOverlayState.renderAfterFinalComposite();
    }
}
