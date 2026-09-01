package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界 poly_mesh GPU 绘制的<b>真正</b>消费点：{@code PreparedFrame.executeSolid} 的 RETURN。
 *
 * <h2>为什么不能沿用 renderAllFeatures 注入点（首版世界烘焙的病根）</h2>
 *
 * <p>MV-PROBE v1/v2 字节码取证（minecraft-merged-26.2，全部偏移实读）：</p>
 * <ol>
 *   <li><b>26.2 的世界实体不经过 {@code renderAllFeatures}</b>。
 *       {@code LevelRenderer.render} 的结构是 {@code prepareFrame}（偏移 90）→
 *       帧图 {@code addMainPass}，其 lambda <b>直接调</b>
 *       {@code PreparedFrame.executeSolid}（lambda$addMainPass$0 偏移 177）/
 *       {@code executeTranslucent}（281）等。挂在 {@code renderAllFeatures} 上的
 *       钩子在真正的世界 pass 里从来不会触发；</li>
 *   <li>全 jar 常量池扫描：调 {@code renderAllFeatures} 的只有
 *       {@code GuiItemAtlas}、{@code PictureInPictureRenderer}（均为 GUI）、
 *       {@code GameRenderer.renderItemInHand}（偏移 185，手部）、
 *       {@code GameRenderer.renderLevel} 偏移 560（在 {@code LevelRenderer.render}
 *       <b>返回之后</b>）。首版世界表就是被 560 那次消费的；</li>
 *   <li>MV 栈时序：{@code LevelRenderer.render} 开头（30-45）
 *       {@code getModelViewStack().pushMatrix(); mul(viewRotation)}，
 *       帧图执行（572）在 {@code popMatrix}（591）<b>之前</b> ——
 *       所以<b>真世界 executeSolid 期间栈顶 = viewRotation</b>，两个绘制核心
 *       从栈顶取 MV 的做法在这里天然正确；而 560 处栈已 pop 回单位阵，
 *       在那里画 = 丢掉相机旋转整层 = 「枪固定在视角空间」（实测症状）。
 *       这与第一人称当年丢 MV_draw 层（0ea0fb6）是同一个病，只是丢法不同：
 *       当年是没乘，这次是在栈已经空了的地方乘。</li>
 * </ol>
 *
 * <p>手部对照组（renderItemInHand 全量 dump）：submit pose 先乘
 * {@code invert(arg3)}（80-93），MV 栈 push {@code arg3}（96-110），
 * {@code renderAllFeatures}（185）后 pop（190）—— 手部钩子挂在
 * renderAllFeatures 上是对的，<b>保持不动</b>。</p>
 *
 * <h2>本注入点的分流（{@code renderWorldAfterSolid} 内部判定）</h2>
 * executeSolid 的调用方有四类，靠两个既有标志区分：
 * <ul>
 *   <li>手部 renderAllFeatures（vanilla 在 LevelRenderer 外、Iris 在其内）——
 *       {@code isInHandPass} 拒收；</li>
 *   <li>GUI 的 renderAllFeatures（GuiItemAtlas / PictureInPictureRenderer）——
 *       {@code insideLevelRender} 为 false 拒收；</li>
 *   <li>镜内那遍 LevelRenderer.render 的帧图 —— <b>各自提交、各自画、画完即清</b>
 *       （2026-09-02 实机改判：每一遍 render 都会把本帧提交节点重画一次，
 *       镜内那遍因此有自己的一份表），但不占 {@code worldDrawnThisFrame}；</li>
 *   <li>主世界帧图 —— 消费 + 置帧标志 + 清表。</li>
 * </ul>
 *
 * <p>时机安全性：executeSolid 内部逐 phase 开/关自己的 render pass，
 * RETURN 处不在任何 pass 内（与 renderAllFeatures 注入点同一论证）；
 * 且世界立方体/地形深度已就绪。绘制失败由 try/catch 降级本会话。</p>
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class PreparedFrameSolidMixin {

    @Inject(method = "executeSolid", at = @At("RETURN"))
    private void tacz$worldPolyMeshAfterSolid(CallbackInfo ci) {
        PolyMeshGpuRenderer.renderWorldAfterSolid();
    }
}
