package com.tacz.guns.mixin.client.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.client.render.scope.ScopeLateReticleState;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Iris（26.1 分支，1.11.x）为延后的 TACZ 镜内准星多跑一次较晚的手部 pass。
 *
 * <p>Iris 的 {@code HandRenderer#renderTranslucent} 平时会在「没有任何半透明手持物」时提前
 * 返回（并把收集器 {@code endFrame()} 清空）。TACZ 枪械不是 {@code BlockItem}，因此冻结在
 * {@code HAND_SOLID} 里的准星快照本来没有更晚的收集器可进。本 mixin 只在快照待发时放宽这道
 * 门，然后在 Iris 选中 {@code HAND_TRANSLUCENT} 之后、它自己的 {@code endFrame()} 冲刷之前，
 * 把快照提交进同一个收集器；FBO、shader 与绘制调度仍全部由 Iris 掌握。</p>
 *
 * <p>与 1.21.11（Iris 1.10.7）的版本差异（均对照 IrisShaders/Iris 26.1 分支源码，commit
 * f4c06978f3a1c64869e40cd5cc7c8ed383085cc0）：</p>
 * <ul>
 *   <li>门从 {@code HandRenderer.isAnyHandTranslucent()} 换成了
 *       {@code ItemInHandRenderer.iris$isAnyHandTranslucent()}——Iris 26.1 把这个判定挪到了
 *       {@code ItemInHandInterface}（接口注入进 MC 的 {@code ItemInHandRenderer}），编译产物里
 *       调用点的 owner 是 {@code ItemInHandRenderer}。下面同时挂了 interface-owner 的后备
 *       处理器，两个 target 互斥，require=0，谁匹配谁生效；</li>
 *   <li>手部收集器字段名/类型不变（{@code submitNodeCollector : SubmitNodeStorage}），
 *       {@code WorldRenderingPipeline.setPhase(WorldRenderingPhase)} 的注入目标也不变；
 *       {@code renderTranslucent} 内只有一次 setPhase 调用，ordinal=0 依旧成立。</li>
 * </ul>
 */
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererReticlePassMixin {
    @Shadow
    private SubmitNodeStorage submitNodeCollector;

    /** 主目标：Iris 26.1 编译产物里，门调用点的 owner 是 MC 的 ItemInHandRenderer。 */
    @ModifyExpressionValue(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;iris$isAnyHandTranslucent()Z",
                    remap = false
            ),
            require = 0
    )
    private boolean tacz$runLateHandPassForScopeReticle(boolean hasVanillaTranslucentHand) {
        return hasVanillaTranslucentHand || ScopeLateReticleState.hasPendingReticles();
    }

    /** 后备目标：若某条 Iris 构建把调用点编译成接口 owner，则由它接手（与上面互斥）。 */
    @ModifyExpressionValue(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/mixinterface/ItemInHandInterface;iris$isAnyHandTranslucent()Z",
                    remap = false
            ),
            require = 0
    )
    private boolean tacz$runLateHandPassForScopeReticleInterfaceOwner(boolean hasVanillaTranslucentHand) {
        return hasVanillaTranslucentHand || ScopeLateReticleState.hasPendingReticles();
    }

    @Inject(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;setPhase(Lnet/irisshaders/iris/pipeline/WorldRenderingPhase;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 0
    )
    private void tacz$submitScopeReticleAfterWorldTranslucency(CallbackInfo ci) {
        // The collector is flushed later by HandRenderer's own submitNodeCollector.endFrame().
        // Do not draw immediately here: that would bypass the currently selected HAND_TRANSLUCENT
        // shader. Iris 26.1 flushes inside renderTranslucent (after iris$renderHandsWithCustomRenderer),
        // so submitting right after setPhase(HAND_TRANSLUCENT) still lands inside the same flush.
        ScopeLateReticleState.submitPending(this.submitNodeCollector);
    }
}
