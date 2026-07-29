package me.xjqsh.lrtactical.init;

import cn.sh1rocu.tacz.api.event.PlayerTickEvent;
import me.xjqsh.lrtactical.capability.CustomItemCoolDowns;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「按玩家挂载数据」的存取层 —— 三代实现的终点。
 *
 * <h2>三代演进（说明为什么是现在这样）</h2>
 * <pre>
 * 1.20.1 Forge     : ICapabilityProvider + LazyOptional
 *                    player.getCapability(XxxProvider.CAPABILITY).ifPresent(Xxx::tick)
 * 1.21.1 NeoForge  : AttachmentType（两个 *Provider.java 被整个删除）
 *                    player.getData(ModCapabilities.CUSTOM_COOLDOWN).tick()
 * 26.2 Fabric(本类): WeakHashMap
 *                    ModCapabilities.coolDowns(player).tick()
 * </pre>
 *
 * <h2>为什么用 WeakHashMap 而不是别的</h2>
 * Fabric 26.2 没有 Forge/NeoForge 那套 capability / attachment 设施。
 * 本仓库<b>已经解决过同一个问题</b> —— {@code CapabilityRegistry} 的注释载明
 * 「26.2: CCA 已移除，改用 {@code DataHolderCapabilityProvider} 内置的 WeakHashMap 存储」。
 * 这里<b>沿用同一模式</b>，保持全仓一致，而不是引入第三种做法。
 *
 * <p><b>WeakHashMap 的关键性质</b>：key 是玩家实体本身，实体被 GC 时条目自动消失，
 * 因此<b>不会内存泄漏</b>，也不需要在玩家登出时手动清理。
 *
 * <p><b>并发</b>：单人游戏下客户端与服务端线程共用这份静态 map，
 * 裸 {@code WeakHashMap} 在并发访问下可能自身结构损坏（死循环 / 丢数据），
 * 故必须包 {@code synchronizedMap} —— 与 TACZ 侧
 * {@code DataHolderCapabilityProvider} 的处理完全一致，是踩过坑之后的既定做法。
 *
 * <h2>【本轮修复】为什么必须按端分表</h2>
 * 字节码确认：26.2 的 {@code Entity#equals} 是<b>按实体网络 id 比较</b>
 * （{@code this.getId() == other.getId()}），{@code hashCode} 也只返回 {@code getId()}。
 * 而 {@code WeakHashMap} 用的正是 {@code equals}/{@code hashCode}。
 *
 * <p>单人游戏里客户端玩家与服务端玩家是<b>两个不同对象、但网络 id 相同</b>，
 * 因此在单一 map 里会<b>撞成同一个条目</b>。叠加下面新注册的 tick 驱动后，
 * 这份共享实例会<b>被两端各 tick 一次 = 每游戏刻自增 2</b>，
 * 冷却时间凭空缩短一半。分成两张表即可让两端各自持有独立实例，各被 tick 一次。
 *
 * <p>（不用「{@code IdentityHashMap} 语义的包装 key」是因为：包装对象若只被 map 自己
 * 弱引用，会立刻被回收；若被强引用又会连带钉住玩家实体，两头都不对。
 * 按端分表是同等效果下最简单、无生命周期陷阱的做法。）
 *
 * <h2>与 NeoForge 版的行为差异（有意为之）</h2>
 * NeoForge 的 attachment <b>会随实体存档持久化</b>；本实现<b>不持久化</b>。
 * 对这份数据而言这是<b>可接受且更合理</b>的：
 * 冷却是<b>瞬时运行时状态</b>，退出重进后本就应当清空。
 */
public final class ModCapabilities {
    /** 服务端侧的玩家冷却表。 */
    private static final Map<Player, CustomItemCoolDowns> SERVER_COOL_DOWNS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 客户端侧的玩家冷却表。与服务端<b>必须分开</b>，理由见类注释。 */
    private static final Map<Player, CustomItemCoolDowns> CLIENT_COOL_DOWNS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ModCapabilities() {
    }

    /** 等价于 NeoForge 的 {@code player.getData(CUSTOM_COOLDOWN)}。 */
    public static CustomItemCoolDowns coolDowns(Player player) {
        Map<Player, CustomItemCoolDowns> map =
                player.level().isClientSide() ? CLIENT_COOL_DOWNS : SERVER_COOL_DOWNS;
        synchronized (map) {
            return map.computeIfAbsent(player, CustomItemCoolDowns::new);
        }
    }

    /**
     * 【本轮修复】驱动所有冷却计时器。
     *
     * <h2>为什么非有不可 —— 「一局只能用一次手雷」的根因</h2>
     * {@link CustomItemCoolDowns} 完全照抄原版 {@code ItemCooldowns} 的设计：
     * 靠自身的 {@code tickCount} 自增来判断冷却是否到期，
     * 而 {@code tickCount} <b>只在 {@code tick()} 里 ++</b>。
     *
     * <p>上游有一个 {@code capability/TickHandler}，用 NeoForge 的
     * {@code PlayerTickEvent.Pre} 每 tick 调一次 {@code getData(...).tick()}。
     * <b>移植时这个类整个漏掉了</b>，也没有任何替代调用方 —— 于是：
     * <ol>
     *   <li>{@code tickCount} 永远停在 0；</li>
     *   <li>{@code addCooldown} 写入的 {@code endTime = 0 + cooldown} 永远大于它；</li>
     *   <li>{@code getCooldownPercent} 恒 &gt; 0 → {@code isOnCooldown} <b>恒为 true</b>；</li>
     *   <li>{@code ThrowableItem#use} 里那句 {@code if (!onCooldown) startUsingItem(hand)}
     *       永远不执行 → 再也进不了「按住准备」状态，自然也就投不出去。</li>
     * </ol>
     *
     * <p>这精确对应用户观察到的三个现象：
     * <b>①</b> 一局游戏里手雷只能用一次；
     * <b>②</b> 换另一颗、一百颗都不行（冷却按 {@code cooldown_category} 记，
     * 与「是哪一颗物品」无关，正是本类存在的意义）；
     * <b>③</b> 小退可以恢复（玩家对象重建，map 里换成一份全新的、cooldowns 为空的实例）。
     *
     * <h2>为什么挂在 {@code PlayerTickEvent.START}</h2>
     * 本仓库已有等价通道（{@code cn.sh1rocu.tacz.mixin.common.PlayerMixin} 在
     * {@code Player#tick} 的 HEAD/TAIL 各发一次 START/END），
     * 且 {@code START} 正对应上游用的 {@code PlayerTickEvent.Pre}，语义一致。
     * 该 mixin 目标是 {@code Player}，故<b>客户端与服务端的玩家都会触发</b> ——
     * 配合上面的按端分表，两侧各自的实例都恰好每游戏刻走一次。
     */
    public static void init() {
        PlayerTickEvent.START.register(event -> {
            coolDowns(event.getEntity()).tick();
            combatProperties(event.getEntity()).tick();
        });

        // 【死亡重生必须清表】否则近战/投掷物在死后整局失效，见 onRespawn 的完整分析。
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN
                .register(ModCapabilities::onRespawn);
    }

    /**
     * 玩家死亡重生后，丢弃与旧玩家对象绑定的状态。
     *
     * <h2>为什么非清不可 —— 「死后刀具整局失效」的根因</h2>
     * 字节码确认 {@code PlayerList#respawn} 的行为：
     * <ol>
     *   <li>{@code new ServerPlayer(...)} 创建<b>全新对象</b>（offset 45-63）；</li>
     *   <li>{@code newPlayer.setId(oldPlayer.getId())} —— <b>沿用旧的网络 id</b>（offset 87-90）。</li>
     * </ol>
     *
     * <p>而 {@code Entity#equals/hashCode} 是<b>按网络 id</b> 比较的
     * （字节码确认，见类注释 9.3）。两者叠加就出事了：
     * <ul>
     *   <li>新玩家去查表，{@code computeIfAbsent} <b>命中旧条目</b>
     *       —— 因为 id 相同、equals 判定为「同一个 key」；</li>
     *   <li>拿到的 {@code CombatProperties} / {@code CustomItemCoolDowns}
     *       内部 {@code entity} / {@code player} 字段仍指向<b>已死亡的旧对象</b>；</li>
     *   <li>{@code tick()} 里读的是旧玩家的主手物品与背包槽位，
     *       {@code coolDownTick} 也就<b>停在死亡那一刻的值不再递减</b>；</li>
     *   <li>于是 {@code preAttack} 里 {@code if (coolDownTick > 0) return false;}
     *       <b>永远为真</b> → 再也打不出任何攻击。</li>
     * </ul>
     *
     * <p>这精确对应用户描述的三个现象：
     * <b>①</b> 死后本局无法攻击实体、重击也没反应；
     * <b>②</b> 小退/大退可恢复（玩家对象连同 id 一起重建，旧条目被 GC）；
     * <b>③</b> 再次死亡又复现（同一条链路重演）。
     *
     * <p>与「一局只能用一次手雷」是<b>同一类问题</b>：状态计时器停摆导致冷却永不结束。
     * 那次是根本没人调 {@code tick()}，这次是 {@code tick()} 调在了错误的对象上。
     *
     * <h2>为什么直接 remove 而不是迁移状态</h2>
     * 冷却与连招都是<b>瞬时战斗状态</b>，死亡本就该清空 ——
     * 玩家复活后理应能立刻攻击，而不是接着背负死前的冷却。
     * 这也与 {@code ModCapabilities} 类注释里「不持久化」的既定取舍一致。
     *
     * <p>用 {@code oldPlayer} 和 {@code newPlayer} <b>都移除一次</b>：
     * 两者 id 相同、equals 相等，理论上移除任一即可命中同一条目；
     * 但显式写两次可防御「将来 Mojang 改成不复用 id」的情况 ——
     * 那时旧条目会残留成内存垃圾（虽有 WeakHashMap 兜底，但不如显式清理明确）。
     */
    private static void onRespawn(net.minecraft.server.level.ServerPlayer oldPlayer,
                                  net.minecraft.server.level.ServerPlayer newPlayer,
                                  boolean alive) {
        synchronized (SERVER_COOL_DOWNS) {
            SERVER_COOL_DOWNS.remove(oldPlayer);
            SERVER_COOL_DOWNS.remove(newPlayer);
        }
        synchronized (SERVER_COMBAT) {
            SERVER_COMBAT.remove(oldPlayer);
            SERVER_COMBAT.remove(newPlayer);
        }
    }

    /**
     * 客户端侧的同一问题 —— 由 {@code TaCZFabricClient} 每 tick 调用。
     *
     * <h2>为什么客户端不能用事件，只能轮询</h2>
     * 字节码确认 {@code ClientPacketListener#handleRespawn} 同样是
     * 「{@code createPlayer(...)} 新建 {@code LocalPlayer}
     * → {@code setId(oldPlayer.getId())} 沿用旧 id」（offset 264 / 319-322），
     * 因此客户端表也会命中陈旧条目，症状与服务端一致。
     *
     * <p>但客户端<b>没有可用的重生事件</b>：本仓库
     * {@code RefreshClonePlayerDataEvent} 的注释已载明，原先那个
     * {@code ClientPacketListenerMixin} 注入的 {@code ClientLevel#addPlayer}
     * 在 26.2 已不存在，{@code ClientPlayerNetworkEvent.CLONE} <b>永远不会触发</b>。
     *
     * <p>该类给出的替代方案是「每 tick 比对 {@code Minecraft#player} 引用」——
     * 本方法<b>照抄这一已验证手法</b>，而不是另造轮子。
     * 每 tick 一次引用比较，开销可忽略。
     *
     * @param current 当前的本地玩家，可能为 {@code null}（未进入世界）
     */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void onClientPlayerTick(net.minecraft.client.player.LocalPlayer current) {
        java.lang.ref.WeakReference<net.minecraft.client.player.LocalPlayer> ref = lastClientPlayer;
        net.minecraft.client.player.LocalPlayer previous = ref == null ? null : ref.get();
        if (current == previous) {
            return;
        }
        lastClientPlayer = new java.lang.ref.WeakReference<>(current);
        if (previous == null) {
            // 首次进入世界，没有旧状态需要清理
            return;
        }
        // 玩家实例被替换（重生 / 跨维度）：丢弃与旧对象绑定的客户端状态。
        synchronized (CLIENT_COOL_DOWNS) {
            CLIENT_COOL_DOWNS.remove(previous);
            if (current != null) {
                CLIENT_COOL_DOWNS.remove(current);
            }
        }
        synchronized (CLIENT_COMBAT) {
            CLIENT_COMBAT.remove(previous);
            if (current != null) {
                CLIENT_COMBAT.remove(current);
            }
        }
    }

    /**
     * 上一次见到的本地玩家实例，用于检测「玩家对象被替换」。
     *
     * <p>用弱引用，避免退出世界后仍钉住旧的 {@code LocalPlayer}
     * —— 与 {@code RefreshClonePlayerDataEvent} 的处理一致。
     */
    private static java.lang.ref.WeakReference<net.minecraft.client.player.LocalPlayer> lastClientPlayer;

    // ---------------- 近战状态机 ----------------

    /** 服务端侧的近战状态。 */
    private static final Map<Player, me.xjqsh.lrtactical.capability.CombatProperties> SERVER_COMBAT =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 客户端侧的近战状态。同样<b>必须与服务端分开</b>，理由见类注释。 */
    private static final Map<Player, me.xjqsh.lrtactical.capability.CombatProperties> CLIENT_COMBAT =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 等价于 NeoForge 的 {@code player.getData(COMBAT_PROPERTIES)}。
     *
     * <p>两端都有各自的实例：客户端那份负责本地位移与 HUD 读数，
     * 服务端那份是<b>权威冷却与真实结算</b>。两者独立计时，允许一两 tick 偏差
     * （{@code preAttack} 在服务端已宽限 1 tick 抵消网络延迟）。
     */
    public static me.xjqsh.lrtactical.capability.CombatProperties combatProperties(Player player) {
        Map<Player, me.xjqsh.lrtactical.capability.CombatProperties> map =
                player.level().isClientSide() ? CLIENT_COMBAT : SERVER_COMBAT;
        synchronized (map) {
            return map.computeIfAbsent(player, me.xjqsh.lrtactical.capability.CombatProperties::new);
        }
    }
}
