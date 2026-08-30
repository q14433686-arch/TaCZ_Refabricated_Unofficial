package com.tacz.guns.mixin.client.iris;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the poly_mesh GPU pass during Iris' solid hand pass（第 2 步 PoC，实验性）.
 *
 * <p>Vanilla 在 {@code GameRenderer#renderItemInHand} 的 RETURN 画 GPU 骨骼；Iris 绕过该方法、
 * 改由 {@code HandRenderer#renderSolid} 驱动手部提交，因此 inHandPass 恒 false、GPU 骨骼永远
 * 不会被提交/绘制。本 mixin 在 {@code renderSolid} 末尾调用 {@link PolyMeshGpuRenderer#renderAfterSolid()}
 * 补齐这个绘制点。</p>
 *
 * <p><b>这是 PoC 脚手架，不是成品：</b></p>
 * <ul>
 *   <li>仅当 {@code MeshGpuUnderShaders=true} 时 GPU 路径才在光影下启用（{@code isGpuPathUsable}），
 *       默认配置下本 mixin 什么都不画；</li>
 *   <li>绘制仍走自定义 RenderPass（目标 = 主渲染目标）。Iris 1.10.7 是否按
 *       {@code IrisApi.assignPipeline(HAND)} 拦截自定义 pass、把枪体路由进 gbuffers_hand，
 *       是 PoC 要实机验证的核心假设 —— 尚未验证；</li>
 *   <li>{@code renderAfterSolid} 自带 try/catch：任何失败都会回退 collector 并禁用本会话 GPU。</li>
 * </ul>
 *
 * <p>{@code renderSolid} 的注入点（TAIL）是第一步试探：更理想的位置是「vanilla 手部提交之后、
 * Iris 自己的 endBatch flush 之前」，与 reticle mixin 的 {@code renderTranslucent} 挂钩点对齐。
 * 若实机发现 TAIL 时已离开 HAND_SOLID 的 FBO/着色器状态，再按字节码把它前移。</p>
 */
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisMeshGpuPassMixin {
    @Inject(method = "renderSolid", at = @At("TAIL"), require = 0)
    private void tacz$drawMeshGpuAfterSolidHand(CallbackInfo ci) {
        PolyMeshGpuRenderer.renderAfterSolid();
    }
}
