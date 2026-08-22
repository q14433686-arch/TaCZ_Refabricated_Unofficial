package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import com.tacz.guns.config.client.RenderConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给「镜内那一遍」配一张更小的阴影贴图。
 *
 * <h2>它省的是什么</h2>
 * {@code renderShadows} 是由 Iris 的 {@code MixinLevelRenderer} 驱动的，也就是
 * <b>每次 {@code LevelRenderer.render} 调用一次</b>。二次渲染一帧调两次，
 * 于是<b>整张阴影贴图每帧被渲染两遍</b> —— 而阴影渲染要把地形与实体从光源视角
 * 再画一遍，分辨率常见 2048²/4096²，往往是一帧里最贵的几件事之一。
 *
 * <p>阴影渲染的开销按<b>面积</b>走，所以把瞄具那套的分辨率减半 = 那一遍的阴影只花 1/4 的代价。
 * 而画质损失<b>只落在镜内</b>：主画面那套阴影分辨率一点没动。
 *
 * <h2>为什么不是「两套共用主画面那张阴影图」（原本更想做的那个）</h2>
 * 阴影贴图与视角无关（从光源方向、以相机为中心渲染），两遍的内容几乎完全一样，
 * 所以「共用一张」听起来才是正解。但实际做不了：
 * <pre>
 * IrisSamplers.addShadowSamplers(SamplerHolder, ShadowRenderTargets, ...)
 * </pre>
 * 采样器绑定拿到的是一个<b>具体的 ShadowRenderTargets 实例</b>，
 * 由 lambda 捕获后用来解析纹理 id。也就是说「用哪张阴影图」是在
 * <b>管线构造、程序创建时</b>就定死的，事后改 {@code IrisRenderingPipeline.shadowRenderTargets}
 * 字段<b>不会</b>让已经建好的采样器改指向 —— 只会造出「一半指旧、一半指新」的半吊子状态。
 * 要真共用就得在构造之前替换，而构造过程本身又要用它建计算着色器，
 * 从外部无法干净地插进去。
 *
 * <p>所以退而求其次：不共用，但让瞄具那套<b>从一开始就小</b>。
 * 分辨率同样是构造时读一次就定死的（{@code PackShadowDirectives.getResolution()}），
 * 而这个读取点恰好可以拦 —— 于是采样器捕获到的自始至终是同一张小图，
 * 全程自洽，没有任何半吊子状态。
 *
 * <h2>只在那一个窗口里生效</h2>
 * 只有 {@code IrisScopePipelineCompat} 主动构造瞄具管线的那一小段时间里才改返回值，
 * 其余任何时候（包括主管线构造）原样放行。窗口在 {@code finally} 里关闭。
 */
@Mixin(targets = "net.irisshaders.iris.shaderpack.properties.PackShadowDirectives", remap = false)
public abstract class IrisShadowResolutionMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "getResolution", at = @At("RETURN"), cancellable = true, require = 0)
    private void tacz$smallerShadowMapForScopePass(CallbackInfoReturnable<Integer> cir) {
        if (!IrisScopePipelineCompat.isBuildingScopePipeline()) {
            return;
        }
        float scale = RenderConfig.SCOPE_PIP_SHADOW_SCALE == null
                ? 1.0f
                : RenderConfig.SCOPE_PIP_SHADOW_SCALE.get().floatValue();
        if (scale >= 1.0f) {
            return;
        }
        int original = cir.getReturnValue();
        // 阴影图按 2 的幂对齐更利于驱动，也避免奇怪的尺寸；至少留 256。
        int scaled = Math.max(256, Integer.highestOneBit(Math.max(1, Math.round(original * scale))));
        if (scaled >= original) {
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Scope pass gets a {}x{} shadow map instead of {}x{}. The "
                            + "shadow pass runs once per world render, so rendering twice per frame "
                            + "doubled it; cost scales with area, so this cuts that pass' shadow work to "
                            + "about {}%. Only the lens is affected.",
                    scaled, scaled, original, original,
                    Math.round(100.0 * scaled * scaled / ((double) original * original)));
        }
        cir.setReturnValue(scaled);
    }
}
