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
 * 给「镜内那一遍」配一套<b>独立的 Iris 管线</b>，把它的时域状态与主画面隔开
 * （26.2 同名 mixin 的移植）。
 *
 * <h2>它修的是什么</h2>
 * 二次渲染在光影下一帧要跑两遍完整管线。而 Iris 所有「上一帧」类的状态都是
 * <b>读一次推进一次</b>的：一帧读两遍，主画面那一遍拿到的「上一帧」其实是
 * <b>本帧镜内那一遍</b>的值，于是所有靠时域重投影的效果全部失准，实测三种表现同源：
 * 整屏拖影（历史按错误矩阵重投影）、体积云噪点闪烁（累积永远收敛不了）、
 * <b>开镜时镜外整屏发糙</b>（TAA 收不住 SSGI/SSAO/雨的随机采样）——
 * 这条曾被误认成「锐化溢出」。关掉 pack 的 TAA 只能压下第一种，因为病根不是 TAA，
 * 而是「每一份上一帧状态都被推进了两次」。
 *
 * <h2>做法：借 Iris 自己的按维度分管线机制</h2>
 * Iris 的管线是<b>按维度缓存</b>的（{@code PipelineManager.pipelinesPerDimension}）。
 * 只要在镜内那一遍期间让 {@code Iris.getCurrentDimension()} 返回一个专用 id，
 * Iris 就会为它单独建一套管线 —— 独立的 {@code RenderTargets}、独立的程序、
 * 因而<b>独立的那一整族 previous uniform 实例</b>。两遍互不干扰。
 *
 * <h3>为什么这样做不会泄漏显存</h3>
 * 我们<b>不自己持有</b>那套管线 —— 它躺在 Iris 的 map 里，由 Iris 管生死。
 * 切维度／重载光影包时 {@code PipelineManager.destroyPipeline()} 会遍历整个 map
 * 全部销毁，我们这套<b>一并被回收</b>（「自己 new 一套再塞进去」就得自己管回收，
 * 漏一次就是显存泄漏 —— 26.2 因此否掉了那条路）。
 *
 * <h3>切维度不会被误触发</h3>
 * 「维度变了就重建管线」那段挂的是 <b>ClientLevel 切换</b>，不是逐帧。
 * 我们的替换只在镜内那一遍的前后成立（{@link ScopePipRerender#isScopePassIsolated()}），
 * 切世界时早已清零，够不着它。
 *
 * <h2>已知取舍（26.2 同款）</h2>
 * <ul>
 *   <li><b>显存</b>：多一套 Iris 的 colortex，按屏幕分辨率算是几百 MB 级。
 *       嫌大就把 {@code ScopePipIsolatePipeline} 关掉（代价是上面三种伪影回来）；</li>
 *   <li><b>首次开镜会卡一下</b>：那套管线是第一次用到时才编译的，只卡一次
 *       （预热成功则挪到进世界后一次性）；</li>
 *   <li><b>非主世界维度可能取到 fallback 着色器</b>：pack 按维度 id 选目录
 *       （{@code dimension.world0 = minecraft:overworld *}），我们这个 id 它不认识，
 *       会落到带 {@code *} 的那一档。主世界正好就是那一档所以完全正确；
 *       下界／末地的镜内可能用到主世界的着色器。</li>
 * </ul>
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
        // id 的构造与预热都收在 IrisScopePipelineCompat 里，两边共用同一个实例 ——
        // 用两个不同的 id 会让 Iris 建出两套瞄具管线，白白多编译一次、多占一份显存。
        Object id = IrisScopePipelineCompat.scopeDimensionId();
        if (id == null) {
            tacz$resolveFailed = true;
            return;
        }
        if (!tacz$logged) {
            tacz$logged = true;
            GunMod.LOGGER.info("[TACZ Scope] Scope pass is using its own Iris pipeline "
                    + "({}:scope_pip) so its temporal state stays separate from the main view.",
                    GunMod.MOD_ID);
        }
        cir.setReturnValue(id);
    }
}
