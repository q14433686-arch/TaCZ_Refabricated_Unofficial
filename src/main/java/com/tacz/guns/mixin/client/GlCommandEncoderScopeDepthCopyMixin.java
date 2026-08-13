package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

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
                                     @Coerce Object glRenderPipeline,
                                     int instanceCount,
                                     CallbackInfo ci) {
        if (!ScopeDepthCopyState.beforeDraw()) {
            ci.cancel();
            return;
        }
        tacz$forceAlwaysDepthIfNeeded(glRenderPipeline);
    }

    /**
     * 1.21.11 的 {@code DepthTestFunction} 没有 ALWAYS，而 {@code NO_DEPTH_TEST} 会
     * {@code glDisable(GL_DEPTH_TEST)} —— OpenGL 在深度测试禁用时连深度写入一并丢弃。
     * 两条 reticle 管线需要的恰恰是「恒通过 + 仍写深度」，枚举里无法表达。
     * <p>
     * 这里在 vanilla 应用完管线状态之后、真正 glDraw* 之前，把深度测试重新打开并把
     * 比较函数改成 GL_ALWAYS，从而补出这个状态。
     * <p>
     * 只对 TACZ 自己的两条 reticle 管线生效，不影响任何其它绘制。
     */
    @Unique
    private static void tacz$forceAlwaysDepthIfNeeded(Object glRenderPipeline) {
        if (glRenderPipeline == null) {
            return;
        }
        Object info = tacz$pipelineInfo(glRenderPipeline);
        if (!ScopeRenderTypes.needsForcedAlwaysDepth(info)) {
            return;
        }
        // 顺序要紧: 先 enable 再设函数。vanilla 对 NO_DEPTH_TEST 走的是 _disableDepthTest(),
        // 这里重新打开深度测试并把比较函数设成恒通过。
        //
        // 【不要在这里 _depthMask(true)】准星管线声明的是 depthWrite=false，是【故意】的:
        // depth-cleanup 刚把目镜区域恢复成世界远深度，准星若写入自己的手部近深度就会把它
        // 覆盖掉，Iris 之后的雾/水面/云会按手部距离叠加到准星上（实机已证实）。
        // vanilla 会依据管线的 depthWrite 自行设置 glDepthMask，这里不要干预。
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
    }

    /** {@code GlRenderPipeline} 是 record，用其 {@code info()} 取回 {@code RenderPipeline}。 */
    @Unique
    private static @Nullable Object tacz$pipelineInfo(Object glRenderPipeline) {
        try {
            if (tacz$infoAccessor == null
                    || tacz$infoAccessor.getDeclaringClass() != glRenderPipeline.getClass()) {
                tacz$infoAccessor = glRenderPipeline.getClass().getMethod("info");
                tacz$infoAccessor.setAccessible(true);
            }
            return tacz$infoAccessor.invoke(glRenderPipeline);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!tacz$loggedInfoFailure) {
                tacz$loggedInfoFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Cannot read GlRenderPipeline.info(); "
                        + "reticle depth override disabled.", e);
            }
            return null;
        }
    }

    @Unique
    private static @Nullable Method tacz$infoAccessor;

    @Unique
    private static boolean tacz$loggedInfoFailure;
}
