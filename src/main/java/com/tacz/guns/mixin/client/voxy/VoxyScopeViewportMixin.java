package com.tacz.guns.mixin.client.voxy;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * 给「镜内那一遍」配一个<b>独立的 Voxy 视口</b>，而不是让它跟主画面共用一个。
 *
 * <h2>为什么这是对的做法（而不是把 Voxy 整个跳过）</h2>
 * Voxy 的 LOD 带着一整套<b>跨帧持续</b>的每视口状态：{@code Viewport.frameId}、
 * 层级遮挡遍历、异步节点管理。一帧里用两个不同的投影去推同一个视口，
 * 这套结构就被写坏，而且<b>不会自己复原</b> —— 实测表现为第一次开镜后
 * 镜外远景永久拉丝／错块。
 *
 * <p>关键在于：<b>Voxy 本来就支持多视口</b>。
 * {@code ViewportSelector} 里有 {@code defaultViewport} 和一张
 * {@code extraViewports} 映射，按任意 key 分配独立视口 ——
 * Iris 的<b>阴影通道</b>用的就是这个机制（{@code IRIS_SHADOW_OBJECT}）：
 * <pre>
 * getViewport() {
 *     if (VIVECRAFT_INSTALLED)        → 每只眼睛一个
 *     if (IrisUtil.irisShadowActive()) → getOrCreate(IRIS_SHADOW_OBJECT)
 *     else                             → defaultViewport
 * }
 * </pre>
 * 阴影通道和主画面正是「同一帧、不同投影」的关系 —— 与我们的镜内那一遍<b>完全同构</b>。
 * 所以这里照它的先例，给瞄具再要一个 key 就是了：Voxy 依旧在镜内渲染
 * （<b>镜内能看到超视距的 LOD 地形</b>），而它的持续状态与主画面各走各的。
 *
 * <h3>为什么用反射调 {@code getOrCreate}</h3>
 * 它是 private，且返回泛型 {@code T}（擦除后是 Voxy 自己的 {@code Viewport}）。
 * 本仓库不编译依赖 Voxy，{@code @Shadow}/{@code @Invoker} 都得写出那个返回类型才能对上描述符，
 * 写不出来。反射一次拿到句柄并缓存，代价可以忽略。
 *
 * <p>拿不到句柄就原样放行（返回主画面那个视口）—— 退回到「共用视口」的老行为，
 * 不会因为反射失败把渲染整个弄挂。
 */
@Mixin(targets = "me.cortex.voxy.client.core.rendering.ViewportSelector", remap = false)
public abstract class VoxyScopeViewportMixin {

    /** 瞄具视口在 {@code extraViewports} 里的 key。只要是个稳定的唯一对象即可。 */
    @Unique
    private static final Object TACZ_SCOPE_VIEWPORT_KEY = new Object();

    @Unique
    private static Method tacz$getOrCreate;
    @Unique
    private static boolean tacz$resolveFailed;
    @Unique
    private static boolean tacz$logged;

    @Inject(method = "getViewport", at = @At("HEAD"), cancellable = true, require = 0)
    private void tacz$scopePassGetsItsOwnViewport(CallbackInfoReturnable<Object> cir) {
        // 【两种模式都要走这条】哪怕隔离模式下 Voxy 不绘制，它的
        // setupViewport(...) 依然会被调到，并且会<b>就地改写</b>视口的投影、
        // 屏幕尺寸与 frameId。用主画面那个视口去承接镜内那一遍的窄投影，
        // 就是把主画面的 LOD 状态写坏 —— 那正是「第一次开镜后远景永久错乱」的成因。
        //
        // 所以无论绘不绘制，镜内那一遍都必须拿到<b>自己的</b>视口，
        // 主画面那个从头到尾不被碰。
        if (tacz$resolveFailed || !ScopePipRenderer.isScopePassActive()) {
            return;
        }
        try {
            Method getOrCreate = tacz$getOrCreate;
            if (getOrCreate == null) {
                getOrCreate = this.getClass().getDeclaredMethod("getOrCreate", Object.class);
                getOrCreate.setAccessible(true);
                tacz$getOrCreate = getOrCreate;
            }
            Object viewport = getOrCreate.invoke(this, TACZ_SCOPE_VIEWPORT_KEY);
            if (viewport == null) {
                return;
            }
            if (!tacz$logged) {
                tacz$logged = true;
                GunMod.LOGGER.info("[TACZ Scope] Voxy will render the scope pass into its own viewport, "
                        + "the same way it already handles the Iris shadow pass. Distant LOD terrain "
                        + "shows in the lens without disturbing the main view's LOD state.");
            }
            cir.setReturnValue(viewport);
        } catch (Throwable t) {
            tacz$resolveFailed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Could not give the scope pass its own Voxy viewport; it "
                    + "will share the main one. Distant terrain may render incorrectly while aiming.", t);
        }
    }
}
