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
 * 给「镜内那一遍」配一张更小的阴影贴图（26.2 同名 mixin 的移植）。
 *
 * <h2>它省的是什么</h2>
 * {@code renderShadows} 由 Iris 的 {@code MixinLevelRenderer} 驱动，也就是
 * <b>每次世界渲染调用一次</b>。二次渲染一帧调两次，于是<b>整张阴影贴图每帧被渲染两遍</b>
 * —— 而阴影渲染要把地形与实体从光源视角再画一遍，分辨率常见 2048²/4096²，
 * 往往是一帧里最贵的几件事之一。开销按<b>面积</b>走：减半 = 那一遍只花 1/4；
 * 画质损失<b>只落在镜内</b>，主画面那套阴影分辨率一点没动。
 *
 * <h2>为什么不是「两套共用主画面那张阴影图」</h2>
 * 阴影贴图与视角无关、两遍内容几乎一样，「共用一张」听起来才是正解。但做不了：
 * {@code IrisSamplers.addShadowSamplers(...)} 拿到的是<b>具体的 ShadowRenderTargets
 * 实例</b>，由 lambda 捕获后解析纹理 id ——「用哪张阴影图」在<b>管线构造、程序创建时</b>
 * 就定死了，事后改字段不会让已建采样器改指向。所以退而求其次：让瞄具那套
 * <b>从一开始就小</b>——分辨率同样是构造时读一次定死的
 * （{@code PackShadowDirectives.getResolution()}），而这个读取点恰好可以拦。
 *
 * <h2>只在构造窗口里生效</h2>
 * 只有 {@link IrisScopePipelineCompat} 主动构造瞄具管线的那一小段时间里才改返回值
 * （{@code buildingScopePipeline} 窗口，finally 关闭），其余任何时候（包括主管线构造）
 * 原样放行。
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
        // 【生死回执】告诉兼容层「构造窗口里确实拦到了 getResolution()」。
        // 本注入是 require=0 的软注入，Iris 内部挪个类它就静默失效 ——
        // 兼容层靠这个回执在真构建后核验，把静默失效变成一行明确告警。
        // 放在 scale 判断之前：即便 scale=1.0 不改返回值，「钩子活着」这个事实照样上报。
        IrisScopePipelineCompat.noteShadowResolutionIntercepted();
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
