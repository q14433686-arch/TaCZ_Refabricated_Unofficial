package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「镜内那一遍」用<b>自己的</b> Iris 管线，把它的时域状态与主画面隔开
 * （26.1.2 同名 mixin 的移植；隔离原理与取舍见 {@link IrisScopePipelineCompat} 类注释）。
 *
 * <p>只答这一句：{@code Iris.getCurrentDimension()} 在镜内那遍期间返回 {@code tacz:scope_pip}。
 * Iris 的管线按维度缓存，于是它自行为这个 id 建/取一套独立的 RenderTargets + 程序 +
 * 整族 previous uniform 实例，两遍互不干扰。我们不持有它，切维度/重载包时由 Iris 一并回收。</p>
 *
 * <p>{@code require = 0} 的<b>软</b>注入：Iris 不在或内部结构挪了，本 mixin 静默不生效 ——
 * 那条路下 {@code ScopePipRerender#shaderIsolateSafe()} 因为 id 反射不出来而<b>继续硬拒</b>
 * 光影窄遍（与 26.1.2 的「静默共用主管线」相比更保守：我们宁可什么都不画错，也不要画出一屏拖影）。</p>
 */
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class IrisScopeDimensionMixin {

    @Unique
    private static boolean tacz$logged;
    /** 构造失败过就不再重试：拿不到就退回真实维度，只是没有隔离，不该反复抛异常。 */
    @Unique
    private static boolean tacz$resolveFailed;

    @Inject(method = "getCurrentDimension", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tacz$scopePassUsesItsOwnPipeline(CallbackInfoReturnable<Object> cir) {
        if (tacz$resolveFailed || !ScopePipRerender.isScopePassIsolated()) {
            return;
        }
        // id 的构造收在兼容层里，两边共用同一个实例 —— 两个不同的 id 会让 Iris 建出两套瞄具管线，
        // 白白多编译一次、多占一份显存。
        Object id = IrisScopePipelineCompat.scopeDimensionId();
        if (id == null) {
            tacz$resolveFailed = true;
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Scope pass is using its own Iris pipeline ({}:scope_pip) "
                    + "so its temporal state stays separate from the main view.", GunMod.MOD_ID);
        }
        cir.setReturnValue(id);
    }
}
