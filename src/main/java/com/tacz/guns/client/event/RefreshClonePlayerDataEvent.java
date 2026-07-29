package com.tacz.guns.client.event;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.util.DelayedTask;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/**
 * 当玩家跨越维度时，客户端需要刷新一次玩家的配件属性缓存
 */
@Environment(EnvType.CLIENT)
public class RefreshClonePlayerDataEvent {
    /**
     * 上一次见到的本地玩家实例，用于检测「玩家对象被替换」。
     *
     * <p>用弱引用，避免在切服/退出后仍attach住旧的 LocalPlayer。</p>
     */
    private static WeakReference<LocalPlayer> lastPlayer = new WeakReference<>(null);

    /**
     * 延迟执行是通过这个方法执行的。
     *
     * <p>【第 42 轮】顺带承担「重生/换维度后刷新配件缓存」的触发职责，
     * 取代已失效的 {@code ClientPacketListenerMixin}。</p>
     *
     * <h2>为什么不再用 mixin</h2>
     * 原 {@code ClientPacketListenerMixin} 注入
     * {@code handleRespawn} 里的 {@code ClientLevel#addPlayer} 调用点，
     * 但 26.2 <b>已无 {@code ClientLevel#addPlayer} 这个方法</b>
     * （对 {@code ClientLevel} 逐方法核对无此项，
     * {@code handleRespawn} 完整反汇编里也不存在该调用）。
     * 也就是说 {@code ClientPlayerNetworkEvent.CLONE} 事件<b>永远发不出来</b>，
     * 重生后的配件属性缓存刷新一直是失效的。
     *
     * <h2>为什么改成轮询是合适的</h2>
     * 这个功能本质只是「玩家实例被换掉之后，延迟 10 tick 调一次
     * {@code initialData()}」—— 它<b>不需要精确的时机</b>，
     * 原实现自己就要靠 {@link DelayedTask} 再延迟 10 tick，
     * 因为事件触发时背包尚未同步。
     *
     * <p>而 {@code Minecraft#player} 字段在重生/换维度时会被整体替换成新实例，
     * 因此在这里比对引用即可捕获同一时机，且：</p>
     * <ul>
     *   <li>本方法<b>本来就已注册</b>为 {@code START_CLIENT_TICK} 回调（驱动 DelayedTask），
     *       不新增任何 tick 开销；</li>
     *   <li>每 tick 只做一次引用比较（{@code !=}），代价可忽略；</li>
     *   <li>零 mixin —— 不依赖任何会随版本改名的内部方法。</li>
     * </ul>
     */
    public static void onClientTick(Minecraft client) {
        try {
            detectPlayerSwap(client);
        } catch (Exception e) {
            GunMod.LOGGER.error("Failed to detect local player swap", e);
        }
        try {
            DelayedTask.SUPPLIERS.removeIf(BooleanSupplier::getAsBoolean);
        } catch (Exception e) {
            DelayedTask.SUPPLIERS.clear();
            GunMod.LOGGER.error(e.getMessage(), e);
        }
    }

    private static void detectPlayerSwap(Minecraft client) {
        LocalPlayer current = client.player;
        LocalPlayer previous = lastPlayer.get();
        if (current == previous) {
            return;
        }
        lastPlayer = new WeakReference<>(current);
        if (current == null || previous == null) {
            // null -> 玩家：首次进入世界，PlayerEnterWorld 已负责初始化，这里不重复。
            // 玩家 -> null：退出世界，无需处理。
            return;
        }
        // 走到这里说明玩家实例被替换了（重生 / 跨维度），与原 CLONE 事件语义一致。
        // 同样延迟 10 tick：此刻背包还没同步完，立即读枪械数据会拿不到配件。
        DelayedTask.add(() -> IGunOperator.fromLivingEntity(current).initialData(), 10);
    }
}
