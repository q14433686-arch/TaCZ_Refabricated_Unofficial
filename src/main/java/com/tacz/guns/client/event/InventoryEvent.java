package com.tacz.guns.client.event;

import cn.sh1rocu.tacz.api.event.ClientPlayerNetworkEvent;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;

@Environment(EnvType.CLIENT)
public class InventoryEvent {
    private static final int HOTBAR_WARM_UP_INTERVAL_TICKS = 7;
    private static final int BACKPACK_WARM_UP_INTERVAL_TICKS = 41;

    // 用于切枪逻辑
    private static int oldHotbarSelected = -1;
    private static ItemStack oldHotbarSelectItem = ItemStack.EMPTY;

    /**
     * 上一次见到的本地玩家实例，用于检测「换了一个玩家对象」＝ 进了（新的）世界。
     *
     * <p>用弱引用，避免退出后仍 attach 住旧的 LocalPlayer。
     * 与 {@code RefreshClonePlayerDataEvent} 的做法一致。</p>
     */
    private static WeakReference<LocalPlayer> lastPlayer = new WeakReference<>(null);
    /**
     * 上一次见到的连接。用于区分「进入新世界」与「仅仅跨维度」——
     * 两者都会换 LocalPlayer 实例，但跨维度不会换连接。见 onPlayerChangeSelect。
     */
    private static WeakReference<ClientPacketListener> lastConnectionRef = new WeakReference<>(null);
    /** 进入世界后需要补发一次 draw。见 {@link #onPlayerChangeSelect} 里的说明。 */
    private static boolean pendingRejoinDraw = false;
    /** 自进入世界起的自计数，不用 player.tickCount（重生/换维度时它会归零）。 */
    private static int rejoinTicks = 0;
    /**
     * 补发的延迟。20 tick = 1 秒，足够连接进入 PLAY 阶段并完成背包同步；
     * 与 RefreshClonePlayerDataEvent 用的 10 tick 同量级，取更保守的值。
     */
    private static final int REJOIN_DRAW_DELAY_TICKS = 20;

    public static void onPlayerChangeSelect(Minecraft client, boolean isPhaseEnd) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();

        // 【本轮修复：持枪退出再进入同一存档后，枪械状态不更新、子弹打不出去】
        //
        // <h2>症状与那条决定性线索</h2>
        // 手持枪械退出再进【同一个】存档，子弹数不减、曳光弹不渲染、命中无伤害；
        // 但「首次进入」正常，「交叉进入两个不同存档」也正常，
        // 且 /tacz reload 能恢复。
        //
        // <h2>根因：onPlayerLoggedOut 根本没被调用过，静态字段跨存档残留</h2>
        //
        // 26.2 有【两条】互不相干的退出路径（字节码逐条确认）：
        //   1. 被踢/断线：ClientPacketListener -> Minecraft#clearClientLevel
        //   2. 主动退出到标题：Minecraft#disconnect(Screen,boolean,boolean)
        //      —— 它自己收尾（resetData / level=null / player=null），
        //         【不经过 clearClientLevel】
        // 而我们的登出 hook（MinecraftMixin#tacz$disconnect）只挂在 clearClientLevel 上，
        // 所以玩家最常用的「退出到标题」这条路【永远不会】触发 onPlayerLoggedOut。
        //
        // 后果是 oldHotbarSelected / oldHotbarSelectItem 跨存档残留：
        //   首次进存档  : oldHotbarSelected = -1（初值）  -> 与当前槽位不等 -> 发 draw -> 正常
        //   再进同一存档: 残留仍是上次的槽位，且槽位没变 -> 【条件不成立，draw 从不发出】
        //   交叉进不同存档: 槽位/物品不同 -> 条件成立 -> 发 draw -> 正常
        // 与实测的三种表现完全吻合，也解释了为何这个 bug「早于 Beta-2」——
        // 它自移植之初就在，只是需要「同一存档进两次」这个特定操作才暴露。
        //
        // 服务端那侧的后果：ShooterDataHolder#currentGunItem 只在收到
        // ClientMessagePlayerDrawGun 时才赋值，draw 不发它就恒为 null，直接命中
        //     LivingEntityShoot#shoot: if (data.currentGunItem == null) return NOT_DRAW;
        // 服务端静默拒绝每一次射击 —— 子弹不减、无伤害；而客户端 validateClientShoot 走自己
        // 那套状态，于是表现为「能扣扳机、有动画，但什么都没发生」。
        // /tacz reload 能恢复，是因为 ClientIndexManager#reload() 结尾补了一次
        // draw(ItemStack.EMPTY)，正好反证根因在「draw 没发出」。
        //
        // <h2>修法：不再依赖登出事件，改为检测玩家实例更换</h2>
        //
        // Minecraft#player 在进入任何世界时都会被换成新实例，这是两条退出路径
        // 都绕不过去的事实。这里比对引用即可可靠地捕获「进入了世界」，
        // 顺便把残留的切枪状态一并清掉。
        // （onPlayerLoggedOut 依然保留：断线那条路会走到它，早一点清理无害。）
        LocalPlayer previous = lastPlayer.get();
        if (player != previous) {
            lastPlayer = new WeakReference<>(player);
            // 【必须区分「进新世界」与「跨维度」——否则会误伤跨维度】
            //
            // 客户端的 LocalPlayer 在这两种情况下【都】会被换成新实例，
            // 所以单看实例更换无法区分。但服务端的情况完全不同（字节码确认）：
            //   进世界/重生 : 服务端换新 ServerPlayer，currentGunItem 为 null
            //                 -> 必须补发 draw 重建，否则打不出子弹
            //   跨维度      : ServerPlayer#teleport 只调 setServerLevel，
            //                 【同一个实例】，currentGunItem 一直有效
            //                 -> 什么都不用做
            //
            // 若对跨维度也照做复位，会凭空触发一次切枪：
            // 服务端 draw() 把 drawTimestamp 推到 now + putAwayTime，
            // getDrawCoolDown() 于是返回 drawTime + putAwayTime（默认枪包约
            // 0.3+0.3=0.6 秒），期间 validateClientShoot 判 IS_DRAWING、状态锁也不释放；
            // 再叠加下面 20 tick 的延迟补发，合计约 1.6 秒不能开枪
            // —— 这正是「跨维度后一段时间无法操作枪械」的成因。
            //
            // 判据：跨维度时【连接不断】，ClientPacketListener 实例保持不变；
            // 而进世界/重连必然是一条新连接。用它来区分，比猜维度 id 可靠
            // （同维度重进、多人服换服都能正确归类）。
            // 【死亡重生的例外】重生与跨维度共用客户端入口 handleRespawn，
            // 连接也同样不断，光看连接会把重生误判成跨维度。但服务端那侧
            // 重生走的是 PlayerList#respawn -> new ServerPlayer -> restoreFrom，
            // 【确实换了实例】，currentGunItem 为 null，必须补发 draw。
            // 用「上一个玩家实例已死亡」把它与跨维度区分开。
            ClientPacketListener connection = player.connection;
            boolean sameConnection = connection != null && connection == lastConnectionRef.get();
            lastConnectionRef = new WeakReference<>(connection);
            boolean respawnedFromDeath = previous != null && previous.isDeadOrDying();

            if (!sameConnection || respawnedFromDeath) {
                // 真正换了服务端 ServerPlayer：清掉可能残留自上一存档的切枪状态，
                // 否则「再进同一存档」时槽位比对会误判为「没换过枪」。
                oldHotbarSelected = -1;
                oldHotbarSelectItem = ItemStack.EMPTY;
                pendingRejoinDraw = true;
                rejoinTicks = 0;
            }
            // 纯跨维度：服务端 ServerPlayer 与 supplier 都完好，
            // 不复位、不补发，避免凭空的切枪冷却。
        }

        // 进世界后延迟若干 tick 再补发一次 draw，手段与 /tacz reload 一致。
        // 之所以还需要这一步（而不是只靠上面复位 oldHotbarSelected）：
        // 复位后下一 tick 确实会发出 draw，但那一刻连接可能尚未进入 PLAY 阶段，
        // 服务端玩家实体也未必就绪，包可能被丢弃。延迟补发保证至少有一次能送达。
        //
        // 用自计数而不是 DelayedTask：后者在切世界时不会被清空，
        // 可能把上一个世界排队的任务带过来。
        // 不用 player.tickCount：重生/换维度时它会归零，可能重复补发。
        // 只在 START 相位累加：本方法在 START/END 各注册了一次，否则一个 tick 算两次。
        // 只在确实手持枪械时补发，避免给空手玩家发无谓的包。
        if (!isPhaseEnd && pendingRejoinDraw && ++rejoinTicks >= REJOIN_DRAW_DELAY_TICKS) {
            pendingRejoinDraw = false;
            if (IGun.mainHandHoldGun(player)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);
            }
        }

        // 玩家切换选中框的情况
        if (oldHotbarSelected != inventory.getSelectedSlot()) {
            ClientIndexManager.warmUpItem(inventory.getItem(inventory.getSelectedSlot()));
            if (oldHotbarSelected == -1) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);
            } else {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(inventory.getItem(oldHotbarSelected));
            }
            oldHotbarSelected = inventory.getSelectedSlot();
            oldHotbarSelectItem = inventory.getItem(inventory.getSelectedSlot()).copy();
            return;
        }
        // 玩家选中的物品改变的情况
        ItemStack currentItem = inventory.getItem(inventory.getSelectedSlot());
        if (currentItem.getItem() instanceof IAnimationItem item) {
            if (!item.isSame(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(oldHotbarSelectItem);
            }
        } else {
            if (!ItemStack.matches(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(oldHotbarSelectItem);
            }
        }

        if (!ItemStack.matches(oldHotbarSelectItem, currentItem)) {
            oldHotbarSelectItem = currentItem.copy();
        }
        if (isPhaseEnd) {
            if (player.tickCount % HOTBAR_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpEquippedAndHotbarModels();
            }
            if (player.tickCount % BACKPACK_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpBackpackModels();
            }
        }
    }

    public static void onPlayerSwapMainHand(SwapItemWithOffHand event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IClientPlayerGunOperator.fromLocalPlayer(player).draw(player.getMainHandItem());
    }

    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 离开游戏时重置客户端 draw 状态
        oldHotbarSelected = -1;
        oldHotbarSelectItem = ItemStack.EMPTY;
        // 下次进世界要重新补发一次 draw，否则服务端的 currentGunItem 会一直是 null。
        pendingRejoinDraw = true;
        rejoinTicks = 0;
    }

    private static boolean isSame(ItemStack i, ItemStack j) {
        IGun iGun1 = IGun.getIGunOrNull(i);
        IGun iGun2 = IGun.getIGunOrNull(j);
        if (iGun1 != null && iGun2 != null) {
            return iGun1.getGunId(i).equals(iGun2.getGunId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return ItemStack.matches(i, j);
    }
}
