package me.xjqsh.lrtactical.client.input;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 兜底：客户端若陷进一个<b>服务端并不存在</b>的使用状态，最多卡若干 tick 就自己爬出来。
 *
 * <h2>为什么还需要它（{@code use} 两端都查冷却之后）</h2>
 * 「客户端也查冷却表」把已知的分叉来源堵掉了，但那是<b>按已知原因</b>修的。
 * 只要客户端还会乐观地 {@code startUsingItem}，就仍存在其它进入分叉的路径
 * （冷却包尚未到达的那一两个 tick、第三方模组改动使用链、数据包热重载导致
 * 客户端一时查不到 index 而 {@code orElse(false)} 放行……）。
 * 而分叉的后果特别难缠：投掷物 {@code getUseDuration} 是 72000，
 * 客户端这轮使用<b>永远不会自己结束</b>，玩家只能松手才能恢复。
 *
 * <p>本类不试图枚举原因，只守住一条<b>可判定的不变量</b>：
 * 对<b>可预燃</b>的投掷物，服务端必然在 {@code prepare_time + life_time} 那一刻
 * 在手心里引爆（{@code ThrowableItem#onUseTick}）。所以客户端的使用时长一旦明显
 * 越过这个点还停着，就<b>一定是</b>分叉状态 —— 不存在合法情形。</p>
 *
 * <h2>为什么它不会误伤正常的预燃</h2>
 * <ul>
 *   <li>只处理 {@code cookable=true} 的投掷物。默认数据里只有
 *       {@code test_grenade}(prepare 4 / life 60) 与 {@code test_flashbang}(4/40) 属于此类；
 *       <b>非预燃</b>投掷物可以合法地一直按着不松（等着投），一律不碰。</li>
 *   <li>{@code life_time <= 0}（如遥控 C4 的 -1）直接跳过 —— 那种配置下
 *       「prepare + life」没有意义。</li>
 *   <li>阈值额外留 {@link #LATENCY_MARGIN_TICKS} 的余量。两端的
 *       {@code getTicksUsingItem()} 各自计数、相差约一个单向延迟，
 *       20 tick(1 秒) 的余量意味着只有延迟超过 1 秒时才可能提前收手 ——
 *       而那种网络下游戏本身已经不可玩。真被提前收手时后果也很轻：
 *       服务端照样会引爆并同步实体与物品消耗，客户端只是动画早停了半秒。</li>
 * </ul>
 *
 * <h2>为什么用 {@code stopUsingItem()} 而不是 {@code releaseUsingItem()}</h2>
 * {@code releaseUsingItem()} 是「客户端通知服务端松手」的那条路
 * （会走 {@code MultiPlayerGameMode#releaseUsingItem} 发包，并回调
 * {@code Item#releaseUsing} —— 在投掷物上那就是<b>真的把手雷扔出去</b>）。
 * 这里要做的恰恰相反：只是本地承认「这轮使用不成立」，不发包、不触发投掷。
 * {@code stopUsingItem()} 是纯本地操作，正是需要的语义。
 *
 * <p>收手之后 {@link UsePressGate} 会在下一个 tick 采到使用状态的下降沿并上锁，
 * 因此不会立刻又被原版输入循环重开一轮 —— 两者是「兜底 + 防复发」的组合。</p>
 */
@Environment(EnvType.CLIENT)
public final class StuckUseRecovery {

    /**
     * 判定分叉前留给网络延迟的余量（tick）。见类注释：只有单向延迟超过这个值
     * 才可能提前收手，且后果只是动画早停。
     */
    private static final int LATENCY_MARGIN_TICKS = 20;

    private StuckUseRecovery() {
    }

    /** 每客户端 tick 末尾调用（与 {@link UsePressGate#onClientTick} 同一处注册）。 */
    public static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof IThrowable throwable)) {
            return;
        }
        ThrowableData data = throwable.getThrowableIndex(stack)
                .map(index -> index.getData())
                .orElse(null);
        if (data == null || !data.isCookable()) {
            // 非预燃投掷物：一直按着等投是合法操作，不能碰。
            return;
        }
        int life = data.getEntityData().getLifeTime();
        if (life <= 0) {
            // 遥控/无限引信（如 C4 的 -1）：没有「最长按住时长」可言。
            return;
        }
        int limit = data.getPrepareTime() + life + LATENCY_MARGIN_TICKS;
        if (player.getTicksUsingItem() > limit) {
            // 纯本地收手：不发包、不触发 releaseUsing（那会真的把手雷扔出去）。
            player.stopUsingItem();
        }
    }
}
