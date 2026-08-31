package com.tacz.guns.mixin.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code LevelRenderer#allChanged}（整套世界渲染器重建：改区块视距、F3+A、换资源包）的
 * 镜内二遍防护。26.2 的 {@code LevelExtractorScopePassMixin} 同构 —— 那边这个入口在
 * {@code LevelExtractor} 上，本世代（26.1.2，javap 实证）还在 {@code LevelRenderer} 本尊。
 *
 * <h2>两件事</h2>
 * <ol>
 *   <li><b>镜内那遍期间取消</b>：Iris 会在一套管线首次渲染时请求一次 full reload，
 *       若发生在镜内那遍里，Voxy 会把自己重新绑到瞄具管线上并持续整个会话 ——
 *       主画面的远景永久错乱。它刷新的 block-id 状态是全局的、主管线早已设好，
 *       取消没有副作用（26.2 同款论断）。</li>
 *   <li><b>货真价实的重载 → 通知 Voxy 兼容层</b>：Voxy 挂在这条路径上的
 *       {@code voxy$reload} 会把整个 {@code VoxyRenderSystem} 拆了重建，我们为镜内
 *       建的第二套渲染栈会当场变成一堆已释放的 GL 对象 —— 必须立刻归还并打回
 *       「需要重新检查」状态，详见 {@code VoxyScopePipelineCompat#onRendererRebuilt}。</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererAllChangedScopePassMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
    private void tacz$noFullReloadDuringScopePass(CallbackInfo ci) {
        if (IrisScopePipelineCompat.isBuildingScopePipeline()) {
            // 【实机崩溃修复 2026-09-01】预热的 preparePipeline 窗口内，scope 管线是
            // 「当前管线」，而管线构建本身会触发一次 allChanged —— 此时放行 = Voxy 系统
            // 若恰在此刻全量重建，会把主栈绑到 scope 管线上；之后空闲释放销毁 scope
            // 管线，主 Voxy 栈就攥着一堆已销毁的 RenderTargets，宽遍地形一画即崩
            // （log 实证：prewarm "Creating pipeline for dimension tacz:scope_pip" 之后的
            // "Shutting down/Creating Voxy render system" 与最终 IllegalStateException
            // Tried to use destroyed RenderTargets）。block-id 状态是全局且早已设好，
            // 取消没有副作用。被取消的重载没有执行，也无需通知 Voxy 兼容层。
            ci.cancel();
            return;
        }
        if (!ScopePipRerender.isInsideScopeLevelRender()) {
            // 这是一次<b>货真价实</b>的重载（玩家改了区块视距、按了 F3+A、换了资源包）。
            IrisScopePipelineCompat.onLevelRendererReload();
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Suppressed a full renderer reload requested during the "
                    + "scope pass. Iris asks for it once when a pipeline first renders, and it would "
                    + "make Voxy rebind itself to the scope pipeline for the rest of the session, "
                    + "permanently corrupting distant terrain in the main view. The block-id state it "
                    + "refreshes is global and already set by the main pipeline.");
        }
        ci.cancel();
    }
}
