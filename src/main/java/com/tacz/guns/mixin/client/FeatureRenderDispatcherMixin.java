package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * poly_mesh 世界 GPU 表的消费点：{@code FeatureRenderDispatcher#renderSolidFeatures} 返回处。
 *
 * <h2>为什么是这里（26.1.2 merged jar 字节码实测，CI 探针 round 4，2026-09-01）</h2>
 * <p>26.1.2 的世界几何 flush 拓扑与 1.21.11 <b>不同构</b>——这正是「不开光影时第三人称/
 * 掉落物形态永不烘焙、开光影时烘焙却粘在视界空间」两个实机症状的共同移植缺口：</p>
 * <ul>
 *   <li><b>1.21.11</b>：{@code LevelRenderer} 的 frame-graph 主 pass 节点内调
 *       {@code renderAllFeatures()} —— 消费点挂它的 RETURN 时位于
 *       {@code GameRendererMixin} 的 {@code levelRenderActive} 窗口内、模型视图槽
 *       正是 vanilla 世界批次 draw 当刻现取的那份（1211 侧 javadoc 的
 *       「MV 不能取自别的时刻」由此天然成立）。</li>
 *   <li><b>26.1.2</b>：{@code renderAllFeatures()} 只有两个站位 ——
 *       {@code ItemInHandRenderer#renderHandsWithItems} 尾部 @281（vanilla 手部尾；
 *       被 {@code inHandPass} 门拒收）与 {@code GameRenderer#renderLevel} 尾部 @570
 *       （在 {@code renderItemInHand} @517 <b>之后</b>——此时 redirect 窗口已关、
 *       {@code levelRenderActive==false} 拒收且不记存活证明 ⇒ 世界表永不消费、
 *       {@code worldFlushAlive()} 恒假、提交侧永久回退 collector = <b>症状①</b>；
 *       Iris 26.1 又把手部搬进 {@code LevelRenderer} 内部执行，其 flush 落在窗口内
 *       但模型视图槽已被手部污染 = 烘焙出来粘在视界空间 = <b>症状②</b>）。</li>
 *   <li><b>26.1.2 的世界实心 flush 真身</b>：主 pass lambda（{@code lambda$addMainPass$0}，
 *       在 {@code LevelRenderer#renderLevel} 内执行）@261 "renderSolidFeatures" →
 *       @273 {@code renderSolidFeatures()}，随后 @390 translucent、@537 clear。
 *       vanilla 的实心世界批次就在这里 draw（26.1.2 的 {@code RenderType#draw} 在绘制
 *       时刻现取 {@code RenderSystem.getModelViewMatrix()}，本轮探针再次确认），本钩子
 *       挂它的 RETURN：同一时刻、同一 MV 槽、同一输出目标、同一深度状态 —— 与
 *       collector「pose 烘进顶点 + 同一份 MV」逐帧等价，1211 的语义按 26.1.2 拓扑落地。</li>
 * </ul>
 *
 * <h2>其余站位的拒收矩阵（全部既有闸门自动覆盖，无需新代码）</h2>
 * <ul>
 *   <li>手部尾 @281 与 GameRenderer @570 的 {@code renderAllFeatures}（其内部第一步就是
 *       {@code renderSolidFeatures}，同样触发本钩子）：分别被 {@code inHandPass} 与
 *       {@code !levelRenderActive} 拒收，且都不记存活证明；</li>
 *   <li>Iris 的 in-level 手部点：{@code worldConsumedFrame} 首消费守卫 —— 主 pass 必然
 *       先跑、先消费，该点自动跳过；</li>
 *   <li>Iris 阴影遍：{@code IrisCompat#isRenderShadow()} 拒收；</li>
 *   <li>gizmo/always-on-top 节点（{@code lambda$addLateDebugPass$0}，26.1.2 里唯一设置
 *       {@code outputColorTextureOverride} 的站位，探针 @34/@49/@111/@115）：
 *       {@code renderAtWorldFlush} 的 override 门拒收；</li>
 *   <li>PIP 镜内那遍：窗口内、{@code insideScope} 分支画但不清表（既有裁定）；</li>
 *   <li>GUI 调用点：不在 {@code levelRenderActive} 窗口内；{@code ScreenRenderTracker}
 *       兜底。</li>
 * </ul>
 *
 * <p>{@code require = 0}：映射漂移到最坏是这个钩子不注入，提交侧的存活证明
 * （{@code PolyMeshGpuRenderer} 的 {@code worldFlushAlive}）随即失败，世界 mesh 枪自动回
 * collector —— 不是丢几何、也不是崩。</p>
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    @Inject(method = "renderSolidFeatures", at = @At(value = "RETURN"), require = 0)
    private void tacz$consumeMeshGpuWorldFlush(CallbackInfo ci) {
        PolyMeshGpuRenderer.renderAtWorldFlush();
    }
}
