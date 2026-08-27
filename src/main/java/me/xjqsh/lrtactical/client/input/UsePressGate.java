package me.xjqsh.lrtactical.client.input;

import me.xjqsh.lrtactical.api.item.ICustomItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 「一次按压只消耗一次使用」——拦住 LRTactical 物品在<b>右键没松手</b>时的自动重新读条。
 *
 * <h2>它治的是什么病</h2>
 * 用户实测：拿着有使用时长（进度条）的 LR 物品（手雷 / 闪光弹 / 消耗品）长按右键，
 * 物品用完之后如果还不松手，进度条会<b>再读一次</b>、动作也会重来一遍，
 * 但物品不会被消耗；而且只要不松手，姿势与进度就一直<b>卡在最末尾</b>。
 *
 * <h2>根因是原版行为，不是我们哪一行写错了</h2>
 * 26.2 {@code Minecraft#handleKeybinds}（本地 jar 字节码逐条核对，偏移 657-687）：
 * <pre>
 * 663  options.keyUse.isDown()          // while：右键按住期间每 tick 都进
 * 670  rightClickDelay == 0
 * 680  !player.isUsingItem()
 * 687      startUseItem()               // ← 只要「没在使用中」就重新开始
 * </pre>
 * 也就是说：<b>使用一结束，下一个 tick 的原版输入循环就会立刻再开一次</b>。
 * 对原版食物（吃完一个接着吃下一个）这是特性；对 LR 物品是 bug ——
 * 服务端那次使用已经结算完（消耗 / 投出 / 进冷却），客户端却凭空再起一轮：
 * <ul>
 *   <li>消耗品：客户端起用了，服务端因冷却拒绝 → 两端分叉 → 客户端这条读条
 *       走完也不会消耗任何东西（{@code finishUsingItem} 的效果段有
 *       {@code !level.isClientSide()} 门禁），看起来就是「读了个空条」；</li>
 *   <li>投掷物：{@code ThrowableItem#getUseDuration} 与上游一致返回 72000（一小时），
 *       所以这轮凭空重来的使用<b>永远不会自己结束</b> →
 *       {@code isUsingItem()} 恒为 true → Lua 状态机停在 {@code using_hold}（姿势定格）、
 *       HUD 分母是 {@code prepare_time} 而分子一直涨（进度条钉在末尾），
 *       直到玩家松手才恢复。这正是用户描述的「卡住」。</li>
 * </ul>
 *
 * <h2>为什么拦在输入层，而不是拦在 {@code Item#use}</h2>
 * {@code MultiPlayerGameMode#useItem}（字节码核对）把
 * {@code ServerboundUseItemPacket} 放在 {@code startPrediction} 的回调里构造，
 * <b>先于</b> {@code ItemStack#use} 调用 —— 所以只在 {@code use} 里返回 FAIL
 * 拦不住包：服务端照样会被问一次，而服务端没有「这次按压已经用过了」的概念，
 * 于是服务端 {@code startUsingItem}、客户端没有 → 换一个方向的分叉。
 * 拦在 {@code startUseItem} 之前则两头都干净：本地不进入使用状态，包也压根不发。
 *
 * <h2>时序为什么可靠（不是碰运气）</h2>
 * 26.2 {@code Minecraft#tick} 的调用顺序（字节码偏移）：
 * {@code handleKeybinds()} = 181 → {@code ClientLevel#tickEntities()} = 244
 * → {@code ClientLevel#tick(...)} = 379。即<b>输入处理先于实体/世界 tick</b>，
 * 而「使用结束」发生在实体 tick 里（服务端停用时靠同步的
 * {@code DATA_LIVING_ENTITY_FLAGS} 落地）。所以本类挂在
 * {@code ClientTickEvents.END_CLIENT_TICK}（{@code Minecraft#tick} 末尾）时，
 * 一定能在<b>同一次</b> tick 里看到下降沿，而下一次 {@code handleKeybinds}
 * 已经是下一个 tick —— 拦得住，不存在「慢一帧」的窗口。
 *
 * <h2>刻意收窄的范围</h2>
 * <ul>
 *   <li>只对 <b>LR 物品</b>（{@link ICustomItem}）生效：原版食物「按住连吃」、
 *       其它模组的长按物品一概不受影响。</li>
 *   <li>只在<b>右键仍按着</b>时拦：{@code startUseItem} 也可能由 TACZ 的
 *       {@code InteractKey} 主动调用，那种情况不该被本门禁挡住。</li>
 *   <li>投掷物正常投出（松手 → {@code releaseUsing}）时右键已经抬起，
 *       不会上锁，连点投掷的手感不变。</li>
 * </ul>
 *
 * <h2>已知边界（如实记录）</h2>
 * 若某个内容包把 {@code use_duration} 写成 0，使用会在同一个 tick 内起停，
 * 本类在 tick 末尾看不到「使用中」的采样，也就不会上锁 —— 那种配置下
 * 自动重读仍会出现。默认枪包与 LR 官方数据都不是 0。
 */
@Environment(EnvType.CLIENT)
public final class UsePressGate {

    /** 上一次采样时是否处于「使用中」。 */
    private static boolean wasUsing;
    /** 使用中那几 tick 采样到的物品，用于判断刚用完的是不是 LR 物品。 */
    private static ItemStack lastUsedStack = ItemStack.EMPTY;
    /** 本次按压是否已经被消耗掉（要求玩家松手再按）。 */
    private static boolean consumedThisPress;
    /** 跨世界 / 重生 / 退出时用于丢弃陈旧状态的玩家引用。 */
    private static LocalPlayer trackedPlayer;

    private UsePressGate() {
    }

    /**
     * 每客户端 tick 末尾调用（{@code ClientTickEvents.END_CLIENT_TICK}）。
     *
     * <p>挂在末尾而不是开头，是为了在「使用结束」发生的那一次 tick 内就看到下降沿 ——
     * 见类注释的时序论证。</p>
     */
    public static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            // 换人 / 重生 / 进出世界：状态全部作废，避免拿着上一局的锁挡住新玩家。
            trackedPlayer = player;
            reset();
            return;
        }
        if (player == null) {
            return;
        }

        boolean keyDown = mc.options.keyUse.isDown();
        if (player.isUsingItem()) {
            wasUsing = true;
            // 采样「正在用的那件物品」：下降沿时 getUseItem() 已经空了，只能提前存。
            lastUsedStack = player.getUseItem();
        } else if (wasUsing) {
            wasUsing = false;
            // 使用刚结束、而右键还按着、且刚用完的是 LR 物品 → 这次按压算用过了。
            consumedThisPress = keyDown && lastUsedStack.getItem() instanceof ICustomItem;
            lastUsedStack = ItemStack.EMPTY;
        }
        if (!keyDown) {
            // 松手即解锁：下一次按压是玩家的新意图，必须放行。
            consumedThisPress = false;
        }
    }

    /**
     * @return {@code true} 表示应当取消这一次 {@code Minecraft#startUseItem}
     *         （本次按压已经消耗完一次 LR 使用，且右键一直没松）
     */
    public static boolean shouldBlockRestart() {
        if (!consumedThisPress) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // 右键没按着却调到这里（例如 TACZ InteractKey 主动触发），不拦。
        return mc.options != null && mc.options.keyUse.isDown();
    }

    private static void reset() {
        wasUsing = false;
        consumedThisPress = false;
        lastUsedStack = ItemStack.EMPTY;
    }
}
