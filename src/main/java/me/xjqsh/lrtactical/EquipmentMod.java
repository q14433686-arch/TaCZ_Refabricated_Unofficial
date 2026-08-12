package me.xjqsh.lrtactical;

import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.init.ModCreativeTabs;
import me.xjqsh.lrtactical.init.ModCustomTypes;
import me.xjqsh.lrtactical.init.ModEntities;
import me.xjqsh.lrtactical.init.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LRTactical（LesRaisins Tactical Equipements）的 Fabric 26.1.2 非官方移植。
 *
 * <h2>移植来源与授权</h2>
 * <ul>
 *   <li>原作：{@code LesRaisins-Studios/LesRaisins-Tactical-Equipements}
 *       —— Programmer {@code xjqsh}，Artist {@code LeComte}，代码 GPL-3.0；</li>
 *   <li>参照的 1.21.1 NeoForge 移植：{@code Nahiyus512/...}（分支 {@code neoforge1.21.1}）
 *       —— 用作「1.20.1 → 1.21.1 有哪些改动」的地图。</li>
 * </ul>
 *
 * <h2>【重要】本移植<b>不包含</b>原作的美术资源</h2>
 * 原作 readme 明确声明 {@code Art Assets: All Rights Reserved}，
 * 因此本移植<b>只移植代码（GPL-3.0 允许）</b>，
 * 不打包、不分发原作的贴图 / 模型 / 音效。
 *
 * <p>这在架构上是可行的：LRTactical 的内容<b>完全由数据驱动</b> ——
 * 代码注册 throwable / melee / consumable / detonator 四个基础物品，具体有哪些手雷、
 * 哪把刀，全部来自数据包中的 {@code data/<ns>/index/*}。原作 flash shield 尚未移植。
 * 因此本移植的定位是<b>纯前置框架</b>，由第三方内容包（「刀包」）提供实际内容。
 *
 * <h2>与本仓库主体（TACZ）的关系</h2>
 * 本包是<b>独立的附属模组代码</b>，与 {@code com.tacz.guns} 并列。
 * 它依赖 TACZ 的公开 API，但不修改 TACZ 自身。
 */
public final class EquipmentMod {
    public static final String MOD_ID = "lrtactical";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private EquipmentMod() {
    }

    /**
     * 附属模块的统一初始化入口。
     *
     * <p>由 TACZ 主入口 {@code cn.sh1rocu.tacz.TaCZFabric#onInitialize} 调用。
     *
     * <h2>为什么必须显式调用</h2>
     * Fabric 没有 Forge/NeoForge 的 {@code DeferredRegister} 自动注册机制，
     * 各 {@code ModXxx} 类的注册动作写在<b>静态字段初始化</b>里，
     * 而 Java 的类加载是<b>惰性</b>的 —— 没有任何代码引用它们，
     * 这些类就永远不会被加载，注册也就<b>永远不会发生</b>。
     *
     * <p>第 4 步正是漏了这一步：物品/实体的注册代码都写好了，
     * 但没有任何调用方，导致游戏里<b>完全找不到这些物品</b>。
     * 这也是本仓库 TACZ 侧那些「看似空实现的 {@code init()}」存在的理由。
     *
     * <h2>顺序要求</h2>
     * {@link ModItems} 必须在 {@link ModCreativeTabs} 之前 ——
     * 标签页的 {@code icon} 与 {@code displayItems} 会引用物品实例。
     * （实际上 lambda 是延迟求值的，但保持声明顺序更稳妥，也更易读。）
     */
    public static void init() {
        ModItems.init();
        ModEntities.init();
        // 粒子类型注册表两端都要有（服务端 addParticle 也要能查到该类型）
        me.xjqsh.lrtactical.init.ModParticleTypes.init();
        // 状态效果注册表两端都要有（服务端施加效果、客户端查询效果）
        me.xjqsh.lrtactical.init.ModEffects.init();
        ModCustomTypes.init();
        ModCreativeTabs.init();

        // 近战攻击入口：把左键攻击接到 IMeleeWeapon#performAttack。
        // 服务端权威判定，客户端只负责拦截原版攻击（详见该类注释）。
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                me.xjqsh.lrtactical.event.MeleeAttackHandler::onAttackEntity);

        // 冷却计时器的 tick 驱动。
        //
        // 【易漏】这不是「注册某个内容」，而是注册一个每 tick 回调 ——
        // 上游对应 capability/TickHandler（@EventBusSubscriber 自动订阅），
        // 移植时整类漏掉，导致冷却永不结束、手雷一局只能用一次。
        // 详见 ModCapabilities#init 的完整根因分析。
        ModCapabilities.init();

        // 数据包重载时加载 index/throwable/*.json。
        // 复用 TACZ 已有的 AddReloadListenerEvent（Fabric 侧的 AddReloadListener 封装），
        // 与 TACZ 自身的资源加载走同一条通道，时机一致。
        //
        // 注意这是【纯服务端】通道（构造自 ReloadableServerResources），
        // 客户端那份索引靠下面的网络同步获得。
        cn.sh1rocu.tacz.api.event.AddReloadListenerEvent.CALLBACK.register(event -> {
            event.addListener(me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getThrowableIndexManager());
            event.addListener(me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getMeleeIndexManager());
            event.addListener(me.xjqsh.lrtactical.resource.CommonAssetsManager.get().getConsumableIndexManager());
        });

        // 网络层：载荷类型注册必须在公共入口（服务端编码 + 客户端解码都依赖）。
        me.xjqsh.lrtactical.network.LrNetworkHandler.registerPayloads();
        // C2S 接收器同样放公共入口 —— 专用服务器不会执行客户端入口。
        me.xjqsh.lrtactical.network.LrNetworkHandler.registerC2SPackets();

        // 玩家登入 / 数据包重载时把索引同步给客户端。
        //
        // 【必需】索引只在服务端加载，联机时客机那份永远是空的，表现为
        // 「创造栏找不到手雷、名字显示成『投掷物』，但功能一切正常」。
        // 挂的是 TACZ 侧 CommonAssetsManager#OnDatapackSync 用的同一个钩子。
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS
                .register(me.xjqsh.lrtactical.network.LrNetworkHandler::syncToPlayer);

        LOGGER.info("LRTactical (unofficial 26.1.2 port) initialized");
    }
}
