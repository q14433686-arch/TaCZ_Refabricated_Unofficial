package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the scope depth backup, ocular aperture copy, world-depth restore and reticle mask binding
 * after vanilla/Iris bind the real destination FBO and before glDraw*.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderScopeDepthCopyMixin {
    @Inject(method = "drawFromBuffers", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$copyScopeDepth(@Coerce Object glRenderPass,
                                     int baseVertex,
                                     int firstIndex,
                                     int indexCount,
                                     VertexFormat.IndexType indexType,
                                     GlRenderPipeline glRenderPipeline,
                                     int instanceCount,
                                     CallbackInfo ci) {
        if (!ScopeDepthCopyState.beforeDraw()) {
            ci.cancel();
            return;
        }
        tacz$forceAlwaysDepthIfNeeded(glRenderPipeline);
    }

    /**
     * 1.21.11 的 {@code DepthTestFunction} 没有 ALWAYS；reticle 因而先声明为
     * {@code NO_DEPTH_TEST}，再在 vanilla 应用完管线状态后、真正 {@code glDraw*}
     * 前改回「深度测试开启 + GL_ALWAYS」。
     * <p>
     * 这里只补深度<b>测试</b>状态。两条 reticle 管线刻意声明
     * {@code depthWrite=false}，不能在这里重新开启深度写入。
     * <p>
     * {@link GlRenderPipeline#info()} 必须保持为直接、类型安全的调用：Loom 会将该
     * 方法调用 remap 到运行时名称；反射中的 {@code "info"} 字符串不会被 remap。
     * 此逻辑只对白名单中的 TACZ reticle 管线生效。
     */
    @Unique
    private static void tacz$forceAlwaysDepthIfNeeded(GlRenderPipeline glRenderPipeline) {
        if (glRenderPipeline == null
                || !ScopeRenderTypes.needsForcedAlwaysDepth(glRenderPipeline.info())) {
            return;
        }
        // 顺序要紧: 先 enable 再设函数。vanilla 对 NO_DEPTH_TEST 走的是 _disableDepthTest(),
        // 这里重新打开深度测试并把比较函数设成恒通过。
        //
        // 【不要在这里 _depthMask(true)】准星管线声明的是 depthWrite=false，是【故意】的:
        // depth-cleanup 恢复的世界深度必须保留给后续 world/composite pass。这个状态
        // 修复本身不保证 shader pack 不会在更晚的 HAND/composite 阶段改变准星颜色，
        // 但重新写入手部近深度会确定地破坏该恢复结果。vanilla 已按管线声明设置
        // glDepthMask，这里不要干预。
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
    }
}
