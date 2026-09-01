package com.tacz.guns.mixin.client.voxy;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隔离模式下，让 Voxy 在「镜内那一遍」<b>不绘制</b>（但视口照常存在）。
 *
 * <h2>【重要】只能拦绘制，绝不能把视口变成 null</h2>
 * 上一版拦的是 {@code getViewport()} / {@code setupViewport(...)}，让它们返回 null。
 * 那是<b>错的</b>，而且后果很重：Voxy 自己的 uniform 提供者不做判空 ——
 * <pre>
 * VoxyUniforms.getProjection()  →  getViewport().projection      // 直接解引用
 * </pre>
 * 于是 Iris 在 {@code beginLevelRendering} 里更新自定义 uniform 时抛 NPE，
 * 整个镜内那一遍被异常打断，{@code ScopePipRenderer} 把自己永久停用，
 * 画面退回整屏 FOV 变焦 —— 实测日志：
 * <pre>
 * NullPointerException: Cannot read field "projection" because the return value of
 *   "VoxyRenderSystem.getViewport()" is null
 *     at VoxyUniforms.getProjection(VoxyUniforms.java:37)
 *     at ... IrisRenderingPipeline.beginLevelRendering
 * </pre>
 *
 * <p>所以正确的做法是：<b>视口照常给</b>（uniform 拿得到投影，不会炸），
 * 只把真正的绘制 {@code renderOpaque} 取消掉。Voxy 于是在这一遍什么都不画，
 * 但它的状态查询路径全部完好。
 *
 * <p>教训：拦一个 mod 的功能时，要拦<b>输出</b>（绘制），
 * 而不是抽掉它的<b>输入</b>（状态对象）—— 后者会让不判空的调用方当场炸。
 *
 * <h2>为什么隔离模式下 Voxy 必须缺席</h2>
 * {@code VoxyRenderSystem.pipeline} 是 {@code private final}，在
 * {@code RenderPipelineFactory} 里按构造那一刻的当前 Iris 管线绑死，
 * 一个 VoxyRenderSystem 只认一套管线。隔离会造出第二套，
 * Voxy 在没绑上的那套下必然用错着色器与绘制目标。
 * 玩家跑一次 {@code /tacz reload} 让坏的一侧换了边，正是这个绑定唯一性的直接证据。
 *
 * <p>既然必坏其一，就让它坏在可控的一侧：镜内不画 LOD（远景缺失但干净），
 * 主画面完全正确。不隔离时只有一套管线，Voxy 可以照常在镜内画，
 * 那条路见 {@link VoxyScopeViewportMixin}。
 */
/** Voxy 是可选 mod：本类的目标类在编译期与运行期都可能不存在。`@Pseudo` 是 Sponge 给这种 mixin 的正规标记——
 * 它同时让本线的 legacy mixin AP 不再把「target could not be found」判成编译错误（他们 26.1.2 那条线是把整个 AP
 * 关掉的，我方 1.21.11 混淆、refmap 必需，AP 不能关）。运行时是否真的应用由 VoxyCompatMixinPlugin 把关。 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public abstract class VoxyRenderSystemMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "renderOpaque", at = @At("HEAD"), cancellable = true, require = 0)
    private void tacz$skipVoxyDrawInIsolatedScopePass(CallbackInfo ci) {
        if (!ScopePipRerender.shouldSuppressVoxyDraw()) {
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Voxy is sitting out the scope pass while that pass uses "
                    + "its own Iris pipeline. Voxy binds to a single Iris pipeline for its lifetime, so "
                    + "it cannot draw correctly under a second one. The lens will not show distant LOD "
                    + "terrain; the main view stays correct. Set ScopePipIsolatePipeline=false to get "
                    + "LOD in the lens instead, at the cost of temporal artifacts.");
        }
        ci.cancel();
    }
}
