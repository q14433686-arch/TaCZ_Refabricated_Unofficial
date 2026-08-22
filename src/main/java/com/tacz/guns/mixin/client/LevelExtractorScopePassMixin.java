package com.tacz.guns.mixin.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 镜内那一遍期间不允许触发「全量重载」——它会让 Voxy 把自己重新绑到<b>错误的</b>渲染管线上。
 *
 * <h2>这就是那个一直找不到的病根</h2>
 * 症状：开着管线隔离时，主画面或镜内必有一侧的远景永久错乱，
 * 而且跑一次 {@code /tacz reload} 会让坏的那一侧<b>换边</b>。
 *
 * <p>完整因果链（全部字节码实读）：
 * <ol>
 *   <li>{@code IrisRenderingPipeline.beginLevelRendering()} 里有一段只跑一次的初始化：
 *       <pre>
 *       if (!initializedBlockIds) {
 *           WorldRenderingSettings.setBlockStateIds(...);   // 全局静态，与管线无关
 *           WorldRenderingSettings.setBlockTypeIds(...);
 *           Minecraft.getInstance().levelExtractor.allChanged();   // ← 关键
 *           initializedBlockIds = true;
 *       }</pre></li>
 *   <li>Voxy 把 {@code voxy$reload} 挂在 {@code LevelExtractor.allChanged()} 上，
 *       内容是 {@code voxy$shutdownRenderer()} + {@code voxy$createRenderer()}；</li>
 *   <li>重建时 {@code RenderPipelineFactory} 用
 *       {@code Iris.getPipelineManager().getPipelineNullable()} —— 也就是
 *       <b>当下那一刻的当前管线</b> —— 去绑定，而
 *       {@code VoxyRenderSystem.pipeline} 是 {@code private final}，<b>绑完就定死</b>。</li>
 * </ol>
 *
 * <p>于是：<b>第一次开镜</b>时，瞄具那套管线第一次 {@code beginLevelRendering}，
 * {@code initializedBlockIds} 还是 false ⇒ 触发 {@code allChanged()} ⇒
 * Voxy 当场重建并绑到<b>瞄具管线</b>上 ⇒ 此后主画面的 LOD 全用错管线 ⇒ 永久错乱。
 * 而 {@code /tacz reload} 会在<b>主管线</b>当前时再触发一次重建，Voxy 改绑主管线，
 * 坏的一侧就跟着换到镜内 —— <b>玩家观察到的换边现象，正是这条链的直接证据</b>。
 *
 * <p>这也解释了为什么「把着色器编译提前」没能解决问题：预热只<b>创建</b>管线，
 * 而 {@code allChanged()} 是在管线<b>第一次真正渲染</b>时才触发的，
 * 那一刻必然落在镜内那一遍里。
 *
 * <h2>为什么可以直接跳过</h2>
 * 那段初始化真正有意义的部分是 {@code WorldRenderingSettings} 的两个 setter，
 * 它们是<b>全局静态</b>的，主管线早已设好，瞄具管线设的是同一份值。
 * {@code allChanged()} 只是「让区块按新的方块 id 重建一遍」——
 * 值都没变，这次重建本就是多余的。跳过它不丢任何东西，
 * 却挡住了那次会把 Voxy 绑歪的重建。
 *
 * <p>只在镜内那一遍期间跳过；其余任何时候（切世界、重载资源、玩家改配置）
 * {@code allChanged()} 照常工作。
 *
 * <h2>另一半职责：重载<b>真的发生了</b>的时候要通知出去</h2>
 * 同一个方法既是「必须挡住的那次」，也是「必须听见的那次」。
 * 重载照常进行时，Voxy 会把整个 {@code VoxyRenderSystem} 拆了重建，
 * 我们为镜内那一遍建的第二套渲染栈随即变成一堆已释放的 GL 对象 ——
 * 玩家一改区块视距再开镜就必崩，正是这条。
 * 所以「不取消」的那条分支要调 {@link IrisScopePipelineCompat#onLevelRendererReload()}。
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorScopePassMixin {

    @Unique
    private static boolean tacz$logged;

    @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
    private void tacz$noFullReloadDuringScopePass(CallbackInfo ci) {
        if (!ScopePipRenderer.isScopePassActive()) {
            // 这是一次<b>货真价实</b>的重载（玩家改了区块视距、按了 F3+A、换了资源包）。
            // Voxy 挂在这个方法上的 voxy$reload 会把整个 VoxyRenderSystem 拆了重建，
            // 我们为镜内那一遍建的第二套渲染栈会当场变成一堆<b>已释放</b>的 GL 对象。
            // 必须在这里就把它还回去 —— 详见 VoxyScopePipelineCompat#onRendererRebuilt。
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
