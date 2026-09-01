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
 * 镜内那一遍不推进 Voxy 的<b>节点管理</b>（流式加载与淘汰）。
 *
 * <h2>它修的是什么</h2>
 * 症状：镜内画面里 LOD 方块<b>闪烁</b>，超出原版渲染距离的 LOD 区域<b>出现空洞</b>。
 *
 * <p>成因在 {@code AbstractRenderPipeline.innerPrimaryWork}（0.2.19 源码 200-227 行）——
 * 它每个视口都要跑一遍，而里面这三件事是<b>全局共享、有状态</b>的：
 * <pre>
 * this.nodeManager.tick(traversal.getNodeBuffer(), nodeCleaner);   // 流式加载 / 淘汰
 * this.nodeCleaner.tick(traversal.getNodeBuffer());                // visibilityId++ 然后按可见世代淘汰几何
 * this.traversal.doTraversal(viewport);                            // 逐视口，产出这个视口要画什么
 * </pre>
 *
 * <p>一帧跑两遍（主画面 + 镜内）就等于把<b>淘汰的节奏加倍</b>：
 * {@code NodeCleaner.tick} 每次都 {@code visibilityId++}，节点按「多久没被看见」老化，
 * 于是只在主画面里可见的节点会被镜内那一遍的世代推进给提前淘汰掉 ——
 * 下一帧又被重新请求回来。看到的就是<b>闪烁</b>；来不及补回来的那些就是<b>空洞</b>。
 *
 * <h2>做法：只拦「推进状态」的两个 tick，保留 doTraversal</h2>
 * {@code doTraversal(viewport)} 必须照跑 —— 它是逐视口的，产出的是「这个视口画什么」，
 * 不跑镜内就没有东西可画。而两个 {@code tick} 是<b>与视口无关</b>的全局推进，
 * 每帧只该发生一次，交给主画面那一遍去做就够了。
 *
 * <p>两种模式都拦：不论 Voxy 用的是主管线还是瞄具那套，
 * 一帧两次推进全局节点状态都是错的。
 */
/** Voxy 是可选 mod：本类的目标类在编译期与运行期都可能不存在。`@Pseudo` 是 Sponge 给这种 mixin 的正规标记——
 * 它同时让本线的 legacy mixin AP 不再把「target could not be found」判成编译错误（他们 26.1.2 那条线是把整个 AP
 * 关掉的，我方 1.21.11 混淆、refmap 必需，AP 不能关）。运行时是否真的应用由 VoxyCompatMixinPlugin 把关。 */
@Pseudo
@Mixin(targets = {
        "me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager",
        "me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner"
}, remap = false)
public abstract class VoxyNodeTickMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tacz$noNodeTickDuringScopePass(CallbackInfo ci) {
        if (!ScopePipRerender.isInsideScopeLevelRender()) {
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Voxy's node streaming/eviction tick is limited to once per "
                    + "frame. Running it for the scope pass as well doubled the eviction rate, which "
                    + "showed up as flickering LOD blocks and holes past the vanilla render distance.");
        }
        ci.cancel();
    }
}
