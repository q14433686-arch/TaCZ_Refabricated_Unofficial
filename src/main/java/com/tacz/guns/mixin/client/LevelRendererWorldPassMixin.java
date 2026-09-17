package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「此刻在不在 {@code LevelRenderer.render} 里」的括号标志。
 *
 * <p>本类的世界 mesh 注入挂在 {@code LevelRenderer#executeSolid} 上，但同名方法
 * 有四类调用者（世界帧图 / GUI 的 renderAllFeatures / 手部 / renderLevel 偏移
 * 560 的收尾调用）。世界 GPU 表只许在<b>世界帧图那一类</b>消费 —— 判据就是
 * 本标志：只有 {@code LevelRenderer.render} 的帧图执行（字节码偏移 572，
 * 在 MV 栈 push viewRotation 的 30-45 与 pop 的 591 之间）落在这个括号内，
 * 其余三类都在括号外。</p>
 *
 * <p>镜内那一遍是我们自己调的 {@code mc.levelRenderer.render}，同样会进这个
 * 括号 —— 正确：镜内世界枪就该在那一遍画。{@code renderWorldAfterSolid}
 * 内部再按 {@code isInsideScopeLevelRender} 区分「画完即清表、但不占帧标志」
 * ——两遍各自提交、各自消费（2026-09-02 实机改判，见该方法 javadoc）。</p>
 *
 * <p>RETURN 注入在异常路径不触发（镜内那遍的失败被 ScopePipRenderer 捕获），
 * 标志可能泄漏到帧尾 —— {@code PolyMeshGpuRenderer.beginFrame} 每帧兜底归零，
 * 且下一次 render 的 HEAD 会重新置位，自愈。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererWorldPassMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$beginLevelRender(CallbackInfo ci) {
        PolyMeshGpuRenderer.setInsideLevelRender(true);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void tacz$endLevelRender(CallbackInfo ci) {
        PolyMeshGpuRenderer.setInsideLevelRender(false);
    }

    /**
     * <h2>26.3：注入目标从 PreparedFrame#executeSolid 改为 LevelRenderer#executeSolid</h2>
     *
     * <p>26.2 时 {@code PreparedFrame#executeSolid} 自己开/关 render pass，
     * 它的 RETURN 处不在任何 pass 内，可以安全地自开 pass 画世界 mesh。
     * <b>26.3 把 pass 归属倒置</b>：{@code LevelRenderer.render} 先
     * {@code createRenderPass("Solid"/"Main")}（LR:443-451），再把这个 pass
     * 一路传进 {@code LevelRenderer#executeSolid(…, renderPass)}（LR:453）
     * → {@code featureFrame.executeSolid(renderPass)}（LR:526）。
     * 于是原注入点已身处 pass 内部，自开 pass 必撞
     * "Close the existing render pass before creating a new one!"。
     *
     * <p>新目标 {@code LevelRenderer#executeSolid} 的 RETURN 同时满足：
     * <ul>
     *   <li><b>能拿到 vanilla 正在录制的那个 pass</b> —— 它就在形参里。
     *       RETURN 处 pass 依然开着（其生命周期属于 {@code render} 的
     *       try-with-resources，不属于本方法），所以我们<b>不自开 pass</b>，
     *       而是把绘制直接录进传入的这一个（见 renderWorldAfterSolid 的改造）。
     *       附件因此天然与 vanilla 一致，也省掉一次 pass 切换。</li>
     *   <li><b>仍在世界帧图路径内</b> —— 与 GUI/手部那几类调用者天然区分开，
     *       原先靠 {@code LevelRendererWorldPassMixin} 的括号标志来区分，
     *       现在目标类本身就只有世界这一条路径，判据更强。</li>
     *   <li><b>MV 栈顶仍是 viewRotation</b> —— push 在 LR:199-200、pop 在 LR:294，
     *       executeSolid 落在其间，原有的「世界 mesh 需要相机旋转层」约束不变。</li>
     * </ul>
     */
    @Inject(
            method = "executeSolid",
            at = @At("RETURN")
    )
    private void tacz$worldPolyMeshAfterSolid(ChunkSectionsToRender chunkSectionsToRender,
                                              FeatureRenderDispatcher.PreparedFrame featureFrame,
                                              RenderPass renderPass,
                                              CallbackInfo ci) {
        PolyMeshGpuRenderer.renderWorldAfterSolid(renderPass);
    }
}
