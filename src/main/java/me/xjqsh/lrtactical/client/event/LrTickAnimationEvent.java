package me.xjqsh.lrtactical.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer;
import me.xjqsh.lrtactical.client.renderer.item.ThrowableItemRendererWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 驱动 LRTactical 物品的<b>待机 / 行走 / 奔跑</b>动画状态转移。
 *
 * <p>与 TACZ 的 {@code TickAnimationEvent} 是同一件事的两份实现 ——
 * 之所以不能合并，是因为那一份的入口写死了
 * {@code TimelessAPI.getGunDisplay(mainHandItem)}（只认枪械的 display）。
 *
 * <h2>为什么这一步不可省略</h2>
 * 状态机的 {@code trigger(INPUT_IDLE/WALK/RUN)} 必须<b>每 tick</b> 被调用一次。
 * 缺了它，动画会永远停在 {@code draw} 结束时的那一帧：
 * 玩家跑动时刀不摆、站定时也不回到 idle 姿势 ——
 * 看起来像「模型卡住了」，但其实模型和动画都加载成功了。
 *
 * <h2>26.2 差异</h2>
 * <ul>
 *   <li>上游用 NeoForge 的 {@code ClientTickEvent.Post}；这里用 Fabric 的
 *       {@code ClientTickEvents}（注册点在 {@code TaCZFabricClient}）。</li>
 *   <li>上游判断移动用 {@code player.input.getMoveVector().length() > 0.01}，
 *       该方法在 26.2 <b>仍然存在</b>（字节码确认
 *       {@code ClientInput#getMoveVector()Lnet/minecraft/world/phys/Vec2;}），
 *       故照抄。注意<b>不能</b>改用 {@code input.up/down/...} ——
 *       那些字段已被 {@code keyPresses} 取代。</li>
 *   <li>上游用 {@code IClientItemExtensions.of(stack).getCustomRenderer()} 取渲染器；
 *       Fabric 侧改为 {@code BuiltinItemRendererRegistry.INSTANCE.get(item)}。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class LrTickAnimationEvent {
    private LrTickAnimationEvent() {
    }

    /**
     * 每客户端 tick：按玩家移动状态推进主手物品的动画状态机。
     */
    public static void tickAnimation(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (!isLrAnimatedItem(mainHandItem)) {
            return;
        }
        var renderer = BuiltinItemRendererRegistry.INSTANCE.get(mainHandItem.getItem());
        if (!(renderer instanceof AnimateGeoItemRenderer<?, ?> geoRenderer)) {
            return;
        }
        var stateMachine = geoRenderer.getStateMachine(mainHandItem);
        if (stateMachine == null) {
            // 没装内容包 → 没有 display → 没有状态机。属正常情况，不是错误。
            return;
        }

        // 群组服切世界导致的特殊情况：input 可能为 null（TACZ 侧同样有此保护）
        if (player.input == null) {
            stateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
            return;
        }
        if (!player.isMovingSlowly() && player.isSprinting()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_RUN);
        } else if (!player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01) {
            stateMachine.trigger(GunAnimationConstant.INPUT_WALK);
        } else {
            stateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
        }
    }

    /**
     * 第三人称下的动画推进（{@code visualUpdate}）与状态机重初始化。
     *
     * <p>照抄 TACZ 的 {@code TickAnimationEvent#tickAnimation(RenderTickEvent)}：
     * 第一人称由 {@code ItemInHandRendererMixin} → {@code renderFirstPerson} 每帧驱动，
     * 第三人称则没有那条路径，需要在这里补一次 ——
     * 否则第三人称看别的玩家（或自己切到第三人称）时动画不动、音效也不响
     * （{@code visualUpdate} 负责播放动画关键帧上的音效）。
     */
    public static void tickAnimation(RenderTickEvent event) {
        if (event.phase == RenderTickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (!isLrAnimatedItem(mainHandItem)) {
            return;
        }
        if (BuiltinItemRendererRegistry.INSTANCE.get(mainHandItem.getItem())
                instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            if (renderer.needReInit(mainHandItem)) {
                renderer.tryInit(mainHandItem, player, event.renderTickTime);
            }
            renderer.visualUpdate(mainHandItem);
        }
    }

    /**
     * 只处理本模块的物品。
     *
     * <p>按<b>渲染器类型</b>判定而不是物品类型：这样将来新增消耗品/防爆盾时，
     * 只要它们复用同一套渲染器基类就自动纳入，不必回头改这里；
     * 同时也天然排除了 TACZ 自己的枪械（它们由 TACZ 的 {@code TickAnimationEvent} 负责，
     * 两边都处理会导致状态机<b>每 tick 被 trigger 两次</b>）。
     */
    private static boolean isLrAnimatedItem(ItemStack stack) {
        var renderer = BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem());
        return renderer instanceof MeleeItemRenderer || renderer instanceof ThrowableItemRendererWrapper;
    }
}
