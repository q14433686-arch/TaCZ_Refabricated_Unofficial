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
 * <h2>为什么是这里（1.21.11 字节码实测，非推测）</h2>
 * <p>{@code renderAllFeatures()} 在 1.21.11 有三个调用点：</p>
 * <ul>
 *   <li>{@code LevelRenderer} 主通道（frame-graph 的 main pass 节点）里
 *       {@code popPush("renderFeatures") -> renderAllFeatures() -> bufferSource.endLastBatch()}；</li>
 *   <li>{@code LevelRenderer} 的次级节点里 {@code particlesRenderState.submit(...) ->
 *       renderAllFeatures() -> particlesRenderState.reset()}（同一帧的第二次调用）；</li>
 *   <li>{@code ItemInHandRenderer#renderHandsWithItems} 末尾
 *       {@code renderAllFeatures() + endBatch()} —— <b>手部那一次不归这里管</b>，
 *       它由 {@code ItemInHandRendererMixin} 在方法 RETURN 处消费手部表（必须等
 *       {@code endBatch()} 之后，语义见那个 mixin 的注释）。</li>
 * </ul>
 * <p>{@code renderAllFeatures()} 自身以 {@code submitNodeStorage.clear()} 收尾 —— 也就是说
 * 它只是把各 feature renderer 的提交<b>写进 builder</b>，真正 draw 由紧随其后的
 * {@code endLastBatch()} 完成。所以在它的 RETURN 处，地形深度已就绪、本帧实体几何还在
 * builder 里，而 {@code RenderSystem.getModelViewMatrix()} 恰是那些批次待会儿在
 * {@code RenderType#draw} 里要写进 {@code DynamicTransforms.ModelViewMat} 的同一份值
 * （1.21.11 的 {@code RenderType#draw} 就是在 draw 当刻现取该值）。GPU 表用「这份 MV ×
 * submit 当刻的骨骼 pose」，与 collector「pose 烘进顶点 + 同一份 MV」逐帧等价。
 * <b>这就是相对视角固定那类 bug 的根因所在：MV 不能取自别的时刻。</b></p>
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
