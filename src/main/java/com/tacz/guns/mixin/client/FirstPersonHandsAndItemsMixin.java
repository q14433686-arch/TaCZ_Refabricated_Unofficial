package com.tacz.guns.mixin.client;

import com.tacz.guns.api.client.other.KeepingItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收枪保持（{@link KeepingItemRenderer}）的宿主。
 *
 * <h2>26.3 迁移：为什么这个文件是新的</h2>
 *
 * <p>26.2 及以前，{@code ItemInHandRenderer} 一个类同时持有<b>装备动画状态</b>
 * （{@code mainHandItem} / {@code mainHandHeight} / {@code tick}）与<b>第一人称渲染</b>
 * （{@code submitHandsWithItems} / {@code submitArmWithItem}）。26.3 把它拆成了三个类：</p>
 *
 * <table border="1">
 *   <caption>26.3 的第一人称三件套</caption>
 *   <tr><th>类</th><th>职责</th></tr>
 *   <tr><td>{@code net.minecraft.client.player.FirstPersonHandsAndItems}</td>
 *       <td><b>状态/tick 侧</b>：持有 {@code mainHandItem}/{@code offHandItem} 与两手高度，
 *           每 tick 推进换手动画，并 {@code extractRenderState} 到渲染状态对象</td></tr>
 *   <tr><td>{@code net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer}</td>
 *       <td><b>渲染侧</b>：{@code submitHandsWithItems} / {@code submitArmWithItem}</td></tr>
 *   <tr><td>{@code ...renderer.state.level.FirstPersonHandsAndItemsRenderState}</td>
 *       <td><b>渲染状态对象</b>（26.3 新增），由前者填、后者读</td></tr>
 * </table>
 *
 * <p>一个 mixin 不能同时 {@code @Mixin} 两个类，所以原先的 {@code ItemInHandRendererMixin}
 * 必须一分为二。<b>本类拿走状态侧</b>（{@code KeepingItemRenderer} 的实现与 {@code tick}），
 * {@link FirstPersonHandsAndItemsRendererMixin} 拿走渲染侧。</p>
 *
 * <p>{@code KeepingItemRenderer} 必须落在<b>状态侧</b>：它的全部作用就是改写
 * {@code mainHandItem}——这个字段 26.3 只存在于本类。</p>
 */
@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsAndItemsMixin implements KeepingItemRenderer {
    @Shadow
    private ItemStack mainHandItem;
    @Unique
    private ItemStack tacz$KeepItem;
    @Unique
    private long tacz$KeepTimeMs;
    @Unique
    private long tacz$KeepTimestamp;

    /**
     * <b>刻意留空</b> —— 与上游 1.21.1 完全一致。
     *
     * <h2>为什么这里必须什么都不做</h2>
     * 上游同名注入点整段是<b>注释掉</b>的（{@code ItemInHandRendererMixin} 第 38-59 行，
     * 逐行核对过），也就是说 TACZ 从来不干预 vanilla 的装备进度。
     * 移植时这段被「还原」成了可执行代码，反而制造了切枪动画的 bug。
     *
     * <h2>它为什么会打断/加速切枪动画</h2>
     * {@code mainHandHeight} / {@code oMainHandHeight} 正是 vanilla
     * {@code FirstPersonHandsAndItems#tick}（26.2 及以前是 {@code ItemInHandRenderer#tick}）
     * 用来推进<b>换手动画</b>的状态量：
     * <pre>
     * // vanilla tick(): 每 tick 朝目标值逼近，产生"落下-抬起"的过渡
     * this.oMainHandHeight = this.mainHandHeight;
     * this.mainHandHeight += Mth.clamp(target - this.mainHandHeight, -0.4F, 0.4F);
     * </pre>
     * 而 {@code mainHandItem} 决定"现在该画哪把枪"、何时切换到新枪。
     *
     * <p>原先的实现在 HEAD 把这三个量<b>每 tick 强制写死</b>
     * （高度恒为 1.0、物品恒为当前主手物）：
     * <ul>
     *   <li>高度被钉死 → vanilla 的过渡插值失去意义，动画表现为<b>被打断或瞬间完成</b>；</li>
     *   <li>{@code mainHandItem} 被立刻改写成新枪 → 旧枪的收枪动画还没播完就被换掉，
     *       表现为<b>不显示动画</b>；</li>
     *   <li>连续快速切换两把枪时，{@code tacz$KeepItem} 的时间窗与这里的强制写入互相打架
     *       （keep 窗口内写 keepItem、窗口外立刻写新物品），于是出现<b>异常加速</b>。</li>
     * </ul>
     * 这与用户实测「不断切换两把不同的枪时会打断/异常加速甚至不显示动画」完全吻合。
     *
     * <p>TACZ 自己的切枪动画由状态机负责（{@code LocalPlayerDraw#doPutAway} →
     * {@code AnimateGeoItemRenderer#tryExit} 触发 {@code INPUT_PUT_AWAY}，
     * 再由 {@code TickAnimationEvent}/{@code needReInit} 驱动 {@code INPUT_DRAW}），
     * <b>不需要也不应该</b>去改 vanilla 的装备进度。
     *
     * <p>保留这个空注入点而不是整个删掉，是为了留住上面这段说明 ——
     * 避免后来者再次「看到空方法就顺手实现它」。
     *
     * <p><b>26.3 注意</b>：{@code tick} 的签名从无参变成了 {@code tick(LocalPlayer)}。
     * 这里用 {@code method = "tick"} 按名匹配（本类只有这一个 {@code tick}），
     * 形参表不写进 CallbackInfo 之外的位置，因此签名变化不影响本注入点。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    public void cancelEquippedProgress(CallbackInfo ci) {
    }

    @Unique
    @Override
    public void keep(ItemStack itemStack, long timeMs) {
        // 【2026-09-02 语义修正】原守卫是「窗口未过期就直接 return」，后果是连续快速切枪时
        // 第二次收枪**接管不了**窗口：上一把枪的剩余窗口继续生效，第二把枪的 put_away 一帧
        // 都画不出来，而且窗口比它需要的短。现改为**最新一次收枪接管**，只保留原守卫里良性
        // 的那一半——同一把枪、且新请求不会延长窗口时不动它，免得把正在播放的动画截断。
        //
        // 「接管不会用一个静止视模顶掉正在播放的动画」由调用点保证：
        // LocalPlayerDraw#doPutAway 只在 AnimateGeoItemRenderer#hasInitializedStateMachine
        // 成立（旧枪确实一直在被渲染、INPUT_PUT_AWAY 确实已触发）时才调 keep。
        long now = System.currentTimeMillis();
        boolean sameKeptItem = tacz$KeepItem != null
                && ItemStack.isSameItemSameComponents(tacz$KeepItem, itemStack);
        if (sameKeptItem && now + timeMs <= tacz$KeepTimestamp + tacz$KeepTimeMs) {
            return;
        }
        this.tacz$KeepTimeMs = timeMs;
        this.tacz$KeepTimestamp = now;
        this.tacz$KeepItem = itemStack;
        this.mainHandItem = itemStack;
    }

    @Override
    public ItemStack getCurrentItem() {
        if (Minecraft.getInstance().player == null) {
            return mainHandItem;
        }
        if (tacz$KeepItem != null) {
            long time = System.currentTimeMillis() - tacz$KeepTimestamp;
            if (time < tacz$KeepTimeMs) {
                return tacz$KeepItem;
            } else {
                tacz$KeepItem = null;
            }
        }
        return mainHandItem;
    }
}
