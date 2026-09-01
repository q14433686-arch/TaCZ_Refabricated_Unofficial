package com.tacz.guns.config;

import com.tacz.guns.GunMod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cloth 配置界面保存后的显式落盘 —— 修复「每次重进游戏配置被重置」。
 *
 * <h2>根因（26.1.2 线 CI javap 探针三轮实锭；本轮对 FCAP 26.2.x 源码复核同病）</h2>
 * FCAP 26.x 搬入了 NeoForge 的新配置架构（{@code LoadedConfig} + 显式保存），
 * 但 Forge 兼容层的保存桥是断的：
 * <ol>
 *   <li>{@code ConfigTracker.readConfig} 把 TOML 解析进<b>内存</b>
 *       {@code SynchronizedConfig}（老 Forge 是 {@code CommentedFileConfig} + autosave，
 *       写值自动落盘 —— 断桥正是从丢掉 autosave 开始的）；</li>
 *   <li>{@code ForgeConfigSpec$ConfigValue.set(T)} 只写 {@code spec.childConfig}（内存）
 *       —— Cloth 每个 entry 的 {@code setSaveConsumer(ConfigValue::set)} 改的只是内存
 *       （26.2.x 源码 ForgeConfigSpec.java:899-904 实读：set + cachedValue 后直接
 *       return，无任何落盘）；</li>
 *   <li>{@code ForgeConfigSpec.save()} 只在 {@code childConfig instanceof FileConfig}
 *       时落盘（:132-137 实读）—— 新架构下 childConfig 永远是 SynchronizedConfig，
 *       <b>恒为静默 no-op</b>；</li>
 *   <li>FCAP 自己的正规保存函数 {@code LoadedConfig.save()}（写 path + 锁内触发
 *       reloading 回调）存在但无人调用。</li>
 * </ol>
 * 于是玩家在 Cloth 界面保存 → 内存变了 → 文件永远不动 → 重启读回旧 TOML = 「配置重置」。
 * NeoForge 全族（renov 两线）无病：原生 FML 的配置持有方自己接了 LoadedConfig.save()。
 * 26.1.2（FCAP 26.1.5）先发病先修（05170 的 ConfigPersist）；本仓与 1.21.11 的 FCAP
 * 构建同架构同病，只是被旧 TOML 恰好等于默认值掩盖到了现在。
 *
 * <h2>修法（比 05170 的更直，因为 26.2.x 的可见性好一档）</h2>
 * 26.1.5 的 {@code LoadedConfig} 类与 {@code ModConfig.loadedConfig} 字段都是包私有，
 * 05170 被迫用 Accessor 掏 childConfig + TomlWriter 手写文件。<b>26.2.x 不必</b>：
 * {@code ModConfig.getLoadedConfig()} 是 public（Fabric/…/ModConfig.java:68），返回的
 * {@code IConfigSpec.ILoadedConfig} 接口把 {@code save()} 声明为 public
 * （Common-NeoForgeApi/…/IConfigSpec.java:65-77）—— 缺的只是接线。
 *
 * <p>{@code ModConfig} 实例从 {@code ModConfigEvents.loading/reloading} 回调里白拿
 * （参数就是它，TaCZFabric 本来就注册了这两个回调）；Cloth 的
 * {@code ConfigBuilder#setSavingRunnable}（保存流程最后一步，跑在全部 entry 的
 * saveConsumer 之后）调 {@link #saveAll()} → 逐个 {@code getLoadedConfig().save()}：
 * 写盘 + reloading 回调全是 FCAP 自己的语义，零反射零 Accessor 零 mixin。</p>
 *
 * <p>SERVER 配置不碰：世界生命周期所有物，Cloth 面板不编辑它。</p>
 *
 * <p>历史文件注意：本修复只保证「此后改动能存住」。若旧 TOML 里钉着与新默认相反的值
 * （「GPU 烘焙默认开却表现为关」的来源），需要玩家在界面里改一次并保存
 * （或删文件重生成）—— 不追溯改写用户文件。</p>
 */
public final class ConfigPersist {

    /** 由 loading/reloading 回调填充：本 mod 的各类配置的 ModConfig 实例。 */
    private static final Map<ModConfig.Type, ModConfig> TRACKED = new ConcurrentHashMap<>();

    private ConfigPersist() {
    }

    /**
     * 记下一个 ModConfig。挂在 {@code ModConfigEvents.loading} 与 {@code reloading}
     * 回调上（幂等；reloading 时实例不变，重放无害）。
     */
    public static void track(@Nullable ModConfig config) {
        if (config != null && GunMod.MOD_ID.equals(config.getModId())) {
            TRACKED.put(config.getType(), config);
        }
    }

    /**
     * 把本 mod 的 CLIENT 与 COMMON 配置显式落盘。挂在 Cloth
     * {@code ConfigBuilder#setSavingRunnable} 上。
     *
     * <p>逐个失败逐个记，一个坏文件不连坐另一个。</p>
     */
    public static void saveAll() {
        saveOne(ModConfig.Type.CLIENT);
        saveOne(ModConfig.Type.COMMON);
    }

    private static void saveOne(ModConfig.Type type) {
        ModConfig config = TRACKED.get(type);
        if (config == null) {
            return;
        }
        try {
            IConfigSpec.ILoadedConfig loaded = config.getLoadedConfig();
            if (loaded != null) {
                loaded.save();
            }
        } catch (Exception | LinkageError e) {
            GunMod.LOGGER.error("Failed to persist the {} config after the Cloth screen saved; "
                    + "changes apply for this session but may reset on relaunch.", type, e);
        }
    }
}
