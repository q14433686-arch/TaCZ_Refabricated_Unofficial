package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * poly_mesh 世界 GPU 表的消费点：{@code FeatureRenderDispatcher#renderAllFeatures} 返回处。
 *
 * <h2>为什么是这里（26.1.2 字节码实测，本地 merged jar，2026-09-01）</h2>
 * <p>26.1.2 上 {@code renderAllFeatures()} 的全部调用点（常量池扫描 + 方法级字节码）：</p>
 * <ul>
 *   <li><b>世界那一次</b>：{@code GameRenderer#renderLevel(DeltaTracker)} 尾部 @570 ——
 *       {@code renderItemInHand}（@517）之后、紧接 {@code bufferSource.endBatch()}（@575）。
 *       这是本钩子唯一该消费的调用点；</li>
 *   <li>{@code GameRenderer#renderItemInHand} 开头 @9-@22 有一次「清遗留几何」的预 flush
 *       （renderAllFeatures + endBatch）—— 那时 {@code inHandPass} 已由 GameRendererMixin
 *       在 HEAD 置位，{@code renderAtWorldFlush} 的 inHandPass 门拒收，且不记世界钩子的
 *       存活证明；</li>
 *   <li>{@code ItemInHandRenderer#renderHandsWithItems} 尾部 @281 —— 同样被 inHandPass 门
 *       拒收（手部表由 ItemInHandRendererMixin 的钩子消费，且必须等 endBatch 之后）；</li>
 *   <li>四个 GUI 调用点（{@code GuiItemAtlas} / {@code GuiBannerResultRenderer} /
 *       {@code GuiEntityRenderer} / {@code OversizedItemRenderer}）—— GUI 渲染不在
 *       {@code GameRenderer#renderLevel} 内，{@code levelRenderActive} 为 false，拒收；
 *       即使标志因异常卡住，还有 {@code ScreenRenderTracker} 与 outputColorTextureOverride
 *       两道闸。</li>
 * </ul>
 *
 * <p><b>消费时刻的渲染状态（Q1 实测）</b>：26.1.2 的 {@code RenderType#draw(MeshData)}
 * 在绘制时刻现取 {@code RenderSystem.getModelViewMatrix()} 写进 DynamicTransforms
 * （@31-@65：getDynamicUniforms → getModelViewMatrix → writeTransform，且发生在
 * createRenderPass 之前 —— 与「pass 内禁 map」的 UB 顺序一致）；输出目标解析为
 * {@code outputColorTextureOverride} 优先、否则 mainTarget，深度仅当
 * {@code RenderTarget.useDepth} 时挂（@179-@225，含 override）。GPU 表在 RETURN 处用
 * 「这份 MV × submit 当刻的骨骼 pose」，与 collector「pose 烘进顶点 + 同一份 MV」逐帧等价。
 * <b>这就是「相对视角固定」那类 bug 的根因所在：MV 不能取自别的时刻。</b></p>
 *
 * <p>不透明几何的先后不影响正确性（双方都写深度），而且本帧 mesh 枪的半透明部件仍走
 * collector、会在我们之后画 —— 顺序反而更自然。</p>
 *
 * <p>{@code require = 0}：映射漂移到最坏是这个钩子不注入，提交侧的存活证明
 * （{@code PolyMeshGpuRenderer} 的 {@code worldFlushAlive}）随即失败，世界 mesh 枪自动回
 * collector —— 不是丢几何、也不是崩。</p>
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    @Inject(method = "renderAllFeatures", at = @At(value = "RETURN"), require = 0)
    private void tacz$consumeMeshGpuWorldFlush(CallbackInfo ci) {
        PolyMeshGpuRenderer.renderAtWorldFlush();
    }
}
