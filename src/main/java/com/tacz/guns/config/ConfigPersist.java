package com.tacz.guns.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.tacz.guns.GunMod;
import com.tacz.guns.mixin.client.ForgeConfigSpecAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cloth 配置界面保存后的显式落盘 —— 修复「每次重进游戏配置被重置」（26.1.2 独有）。
 *
 * <h2>根因（全部 CI javap 探针字节码实读，compile-java.log 2026-09-01）</h2>
 * FCAP v26.1.5 把 NeoForge 的新配置架构（{@code LoadedConfig} + 显式保存）搬了进来，
 * 但它的 Forge 兼容层没接上桥：
 * <ol>
 *   <li>{@code ConfigTracker.readConfig} 手动解析 TOML 进<b>内存</b>
 *       {@code SynchronizedConfig}（老 Forge 是 {@code CommentedFileConfig} + autosave）；</li>
 *   <li>{@code ForgeConfigSpec$ConfigValue.set(T)} 只写 {@code spec.childConfig}（内存）
 *       —— 即 Cloth 保存回调改的只是内存；</li>
 *   <li>{@code ForgeConfigSpec.save()} 只在 {@code childConfig instanceof FileConfig}
 *       时落盘 —— 新架构下 childConfig 永远不是 FileConfig ⇒ <b>恒为静默 no-op</b>；</li>
 *   <li>于是没有任何环节把改后的值写回 TOML：重启读回旧文件 = 「配置重置」。</li>
 * </ol>
 *
 * <h2>两条落盘路径（2026-09-02 跨线合并，依次尝试）</h2>
 * <ol>
 *   <li><b>FCAP 官方路径</b>（同步自 26.2 线 {@code 7227ff9}）：{@code ModConfig.getLoadedConfig()}
 *       返回的 {@code ILoadedConfig#save()} —— 写盘 + 锁内 reloading 回调全是 FCAP 自己的语义。
 *       26.2.x 上它是 public 接口方法；<b>v26.1.5 的可见性未经证实</b>（本分支第一轮探针只查到
 *       {@code LoadedConfig} 类与 {@code ModConfig.loadedConfig} 字段是包私有，没查
 *       {@code getLoadedConfig()} 这个方法），所以这里用<b>反射</b>探测：命中就用它，
 *       没命中就静默退回路径 2，不因 FCAP 构建差异而编译失败或抛异常。</li>
 *   <li><b>Accessor + 显式写回</b>（本分支第一版，保留为回退）：{@code ForgeConfigSpecAccessor}
 *       取出 {@code childConfig}（{@code set()} 已把新值写进去），写回注册名对应的 TOML。</li>
 * </ol>
 * 两条都只在 Cloth 面板「保存」这一步触发（{@code ConfigBuilder#setSavingRunnable}，
 * 跑在所有 entry 的 saveConsumer 之后），不逐帧写盘。
 *
 * <h2>2026-09-02 补的三处（同步自 1.21.11 线 {@code cd14a2ac}，它们实机测出的断点）</h2>
 * <ol>
 *   <li><b>非标准命名的那份也要落盘</b>：{@code PreLoadConfig} 的 {@code tacz-pre.toml} 装着
 *       {@code DefaultPackDebug}，而 Cloth 面板 {@code OtherClothConfig} 编辑的正是它。
 *       原先 {@link #record} 只登记 {@code <modid>-<type>.toml}，那份永远写不回去 ⇒
 *       新增 {@link #recordNamed}。</li>
 *   <li><b>不再静默跳过</b>：取不到内存配置时改为一条 WARN 点名 —— 否则「配置不持久化」与
 *       「FCAP 还没把 spec 载入」两种病在日志里长得一模一样。</li>
 *   <li><b>原子合并写</b>：目标文件已存在时先读入、把内存值逐项覆盖（保住用户文件里的注释与
 *       键顺序），再经同目录临时文件 + {@code ATOMIC_MOVE} 替换。原先直接
 *       {@code newBufferedWriter} 会<b>先截断</b>目标文件：那一刻若 JVM 被杀/掉电，
 *       留下空 TOML ⇒ 下次启动全部配置回默认且不可恢复。</li>
 * </ol>
 *
 * <p>SERVER 配置不碰：世界生命周期所有物，Cloth 面板不编辑它。</p>
 *
 * <p><b>证据级别（AGENTS §2）</b>：根因 = 字节码实读；路径 1 在 v26.1.5 上是否命中 =
 * <b>未验证</b>（反射探测，命中与否会打一条 INFO）；三处补齐 = 1.21.11 线实机测出、本分支
 * 同形移植、<b>本沙箱无运行环境、实机未验</b>。</p>
 */
public final class ConfigPersist {

    /** 一份要落盘的（spec、文件、标签）。注册顺序即保存顺序。 */
    private record Target(ForgeConfigSpec spec, Path file, String label) {
    }

    private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();

    /**
     * FCAP 的 {@code ModConfig} 实例，按<b>文件名</b>索引（不是按 type —— pre 那份也是
     * COMMON，按 type 存会与真正的 common 互相覆盖）。由
     * {@code ModConfigEvents.loading/reloading} 回调填入，供路径 1 使用。
     */
    private static final Map<String, ModConfig> TRACKED_BY_FILE = new ConcurrentHashMap<>();

    /** 路径 1 是否已探测过：只打一条 INFO，别把日志刷满。 */
    private static volatile int fcapPathLogged = 0;

    private ConfigPersist() {
    }

    /** Forge/FCAP 的默认命名惯例：{@code <modid>-<type>.toml}（显式注册，防默认规则变动）。 */
    private static String fileName(ModConfig.Type type) {
        return GunMod.MOD_ID + "-" + type.toString().toLowerCase(Locale.ROOT) + ".toml";
    }

    /**
     * 注册钩子：在 {@code ConfigRegistry.register} 之前把 spec 交给本类，返回要显式传入的文件名。
     */
    public static String record(ModConfig.Type type, ForgeConfigSpec spec) {
        return recordNamed(spec, fileName(type));
    }

    /**
     * 非标准命名的那份配置也要落盘（{@code PreLoadConfig} 的 {@code tacz-pre.toml}）。
     *
     * @param fileName 已钉死的注册文件名（与 {@code ConfigRegistry.register} 第 4 参同一个值）
     */
    public static String recordNamed(ForgeConfigSpec spec, String fileName) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        if (spec != null) {
            TARGETS.add(new Target(spec, file, fileName));
        }
        return fileName;
    }

    /**
     * 记下 FCAP 的 {@code ModConfig} 实例。挂在 {@code ModConfigEvents.loading} 与
     * {@code reloading} 回调上（幂等；reloading 时实例不变，重放无害）。
     */
    public static void track(@Nullable ModConfig config) {
        if (config != null && GunMod.MOD_ID.equals(config.getModId())) {
            TRACKED_BY_FILE.put(config.getFileName(), config);
        }
    }

    /** 把登记过的所有 spec 落盘。单个文件失败只记日志并跳过，不连带拖垮另一份。 */
    public static void saveAll() {
        if (TARGETS.isEmpty()) {
            GunMod.LOGGER.warn("[TACZ] Config persist: no spec was registered; nothing to save. "
                    + "If the settings screen saved anything, it stayed in memory only.");
            return;
        }
        for (Target target : TARGETS) {
            save(target);
        }
    }

    private static void save(Target target) {
        try {
            if (saveViaFcap(target)) {
                return;
            }
            saveViaAccessor(target);
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] Failed to persist the {} config after it was saved. The values stay "
                    + "effective for this session but revert to the file's contents on next launch.",
                    target.label(), t);
        }
    }

    /**
     * 路径 1：FCAP 自己的 {@code ModConfig#getLoadedConfig()#save()}。
     *
     * <p>26.2 线核到 v26.2.x 上 {@code getLoadedConfig()} 是 public；v26.1.5 未核实，
     * 因此这里反射探测而不是直接引用 —— 命中即用它（写盘 + reloading 回调全是 FCAP 语义），
     * 没命中就返回 false 交给回退路径，绝不因为 FCAP 构建差异把配置保存整条拖死。</p>
     */
    private static boolean saveViaFcap(Target target) {
        ModConfig config = TRACKED_BY_FILE.get(target.label());
        if (config == null) {
            return false;
        }
        try {
            Method getLoadedConfig = config.getClass().getMethod("getLoadedConfig");
            getLoadedConfig.setAccessible(true);
            Object loaded = getLoadedConfig.invoke(config);
            if (loaded == null) {
                return false;
            }
            Method save = loaded.getClass().getMethod("save");
            save.setAccessible(true);
            save.invoke(loaded);
            logFcapPathOnce(true);
            return true;
        } catch (Throwable t) {
            // 反射没命中（方法不存在/不可达）是正常的兼容分支，不是错误：静默退回回退路径。
            logFcapPathOnce(false);
            return false;
        }
    }

    private static void logFcapPathOnce(boolean used) {
        if (fcapPathLogged == 0) {
            fcapPathLogged = 1;
            if (used) {
                GunMod.LOGGER.info("[TACZ] Config persist: using FCAP's own ModConfig save path "
                        + "(getLoadedConfig().save()).");
            } else {
                GunMod.LOGGER.info("[TACZ] Config persist: FCAP's ModConfig save path is not reachable on this "
                        + "FCAP build; falling back to the explicit TOML writer.");
            }
        }
    }

    /** 路径 2：Accessor 取 childConfig 后显式写回（合并写 + 原子替换）。 */
    private static void saveViaAccessor(Target target) throws IOException {
        ForgeConfigSpec spec = target.spec();
        if (spec == null || !spec.isLoaded()) {
            GunMod.LOGGER.warn("[TACZ] Could not persist the {} config: the spec is not loaded yet (FCAP has not "
                    + "handed it a config). Values stay effective for this session only.", target.label());
            return;
        }
        Config child = ((ForgeConfigSpecAccessor) (Object) spec).tacz$getChildConfig();
        if (child == null) {
            // 以前这里是静默 return：FCAP 尚未把配置塞进 spec 时什么都不写、什么都不说，
            // 「配置不持久化」与「FCAP 没加载」两种病在日志里长得一样。
            GunMod.LOGGER.warn("[TACZ] Could not persist the {} config: its spec has no in-memory config yet "
                    + "(FCAP has not loaded it). Values stay effective for this session only.", target.label());
            return;
        }
        if (child instanceof com.electronwill.nightconfig.core.file.FileConfig fileConfig) {
            // 旧架构（老 Forge 的 CommentedFileConfig + autosave）：它自己会写，交回给它，
            // 保住它自己的注释与格式。
            fileConfig.save();
            return;
        }
        Path file = target.file();
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.exists(file)) {
            // 合并写：保住用户文件里的注释、键顺序与未被本 spec 覆盖的条目。
            // 直接 TomlWriter 覆盖会把注释整片抹掉。
            CommentedFileConfig merged = CommentedFileConfig.builder(file)
                    .preserveInsertionOrder()
                    .build();
            try {
                merged.load();
                copyInto(child, merged, Collections.emptyList());
                writeAtomic(merged, file);
            } finally {
                merged.close();
            }
            return;
        }
        writeAtomic(child, file);
    }

    /** 递归把内存配置的叶子值写进目标（路径列表 → 值），中间表自动创建。 */
    private static void copyInto(Config from, CommentedConfig to, List<String> prefix) {
        for (Map.Entry<String, Object> entry : from.valueMap().entrySet()) {
            List<String> path = new ArrayList<>(prefix.size() + 1);
            path.addAll(prefix);
            path.add(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Config sub) {
                copyInto(sub, to, path);
            } else {
                to.set(path, value);
            }
        }
    }

    /**
     * 原子写：先写同目录临时文件再 ATOMIC_MOVE 替换（不支持时退化为普通替换）。
     *
     * <p>直接对目标文件 {@code newBufferedWriter} 会<b>先截断</b>：这一刻若 JVM 被杀/掉电，
     * 留下空文件 —— 下次启动读回「什么都没有」= 全部配置回默认，而且再也找不回来。
     * 配置不持久化最坏的那种形态就是它。</p>
     *
     * <p>{@code TomlWriter} 对 {@code CommentedConfig}（本方法的主要输入）会连注释一起写。</p>
     */
    private static void writeAtomic(Config config, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                new TomlWriter().write(config, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
