package com.tacz.guns.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.tacz.guns.GunMod;
import com.tacz.guns.mixin.client.ForgeConfigSpecAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置落盘的显式收尾 —— 修「重启后配置回到默认」。
 *
 * <h2>病根（与 26.1.2 同一条链，本线由维护者实机确认）</h2>
 * FCAP 把 NeoForge 的新配置架构（{@code LoadedConfig} + 显式保存）搬进 Fabric 后，Forge 兼容层
 * 那半截桥没接上：
 * <ol>
 *   <li>{@code ConfigTracker.readConfig} 把 TOML 手动解析进<b>内存</b> {@code SynchronizedConfig}
 *       （老 Forge 那边是 {@code CommentedFileConfig} + autosave，写内存即写盘）；</li>
 *   <li>{@code ForgeConfigSpec$ConfigValue.set(T)} 只写 {@code spec.childConfig}（内存）
 *       ⇒ Cloth 面板的 {@code setSaveConsumer} 与 {@code /tacz config} 命令改的都只是内存；</li>
 *   <li>{@code ForgeConfigSpec.save()} 只在 {@code childConfig instanceof FileConfig} 时才落盘
 *       ⇒ 新架构下该 instanceof <b>永假</b>，save() 是<b>静默 no-op</b>（不抛、不日志）；</li>
 *   <li>于是没有任何环节把改后的值写回 TOML：下次启动读到旧文件 = 「配置被重置」。</li>
 * </ol>
 *
 * <p>FCAP 自带的正规保存函数 {@code LoadedConfig.save()} 走不通：{@code LoadedConfig} 与
 * {@code ModConfig#loadedConfig} 都是<b>包私有</b>（26.1.2 的第一版修复就是栽在这里、被编译门拒掉）。</p>
 *
 * <h2>本类的做法（对本线两种架构都成立）</h2>
 * 注册时用带文件名的 {@code register} 重载把文件钉在 Forge 惯例名
 * （{@code tacz-client.toml} / {@code tacz-common.toml}，与 FCAP 默认命名一致，不改变现有文件），
 * 保存流程最后一步调 {@link #saveAll()}：
 * <ul>
 *   <li>{@code childConfig} 是 {@code FileConfig}（旧架构，本仓 {@code PreLoadConfig} 就属这一路）⇒
 *       交回 {@code spec.save()} 自己写，保留它的注释与格式；</li>
 *   <li>否则（新架构）⇒ 用 Accessor 取出 {@code childConfig}（{@code set()} 已把新值写进去），
 *       {@code TomlWriter} <b>显式写回</b>注册名对应的 TOML。注释同样保留：{@code SynchronizedConfig}
 *       是带注释解析的。</li>
 * </ul>
 * SERVER 配置是世界生命周期所有物、面板不编辑，也不在本类范围内（保持 FCAP 自己的路径）。
 *
 * <p>调用点只有一处：Cloth 面板 {@code ConfigBuilder#setSavingRunnable} 的最后一步（所有
 * {@code setSaveConsumer} 都跑完之后）。那是"用户明确要求保存"的时刻，不逐帧写盘。</p>
 *
 * <h2>适用范围（刻意不覆盖的部分）</h2>
 * 只有 <b>CLIENT 与 COMMON</b> —— 面板编辑的就是这两份。<b>SERVER</b> 配置（{@code ServerConfig.init()}
 * 里的 {@code SyncConfig} 那一族，含 {@code /tacz config} 改的三条键）不进本类：它的落盘与"首次进世界时
 * 拷贝到 {@code <world>/serverconfigs/}"由 FCAP 自己管，我们只钉了 client/common 两个名字，按 {@code config/}
 * 下的路径去写会写到那份将被覆盖的副本上。所以 {@link #record} 的 {@code default} 分支什么都不做，
 * {@code ConfigCommand} 也不"顺手"调 {@link #saveAll()}（对本命令无作用，纯假动作）。</p>
 *
 * <h2>为什么这个修法对两种架构都成立</h2>
 * {@code childConfig instanceof FileConfig}（旧架构；本仓 {@code PreLoadConfig} 正是这一路）⇒ 交回
 * {@code spec.save()}，行为与今天完全一致；只有新架构（{@code SynchronizedConfig}，此时 {@code save()}
 * 恒 no-op）才由我们显式 {@code TomlWriter} 写回。<b>因此不需要先证明本线 FCAP 21.11.1 走的是哪一条</b>：
 * 分岔在运行时自己判，两种情况都不会写坏文件。这也是本批与 26.1.2 的唯一形状差 —— 他们核实过
 * v26.1.5 必为新架构，所以直接写 TomlWriter，没有这个 instanceof 分支。
 */
public final class ConfigPersist {

    /** Forge/FCAP 的默认命名惯例：{@code <modid>-<type>.toml}（显式注册，防默认命名规则变动）。 */
    private static String fileName(ModConfig.Type type) {
        return GunMod.MOD_ID + "-" + type.toString().toLowerCase(Locale.ROOT) + ".toml";
    }

    @Nullable
    private static volatile Path clientFile;
    @Nullable
    private static volatile ForgeConfigSpec clientSpec;
    @Nullable
    private static volatile Path commonFile;
    @Nullable
    private static volatile ForgeConfigSpec commonSpec;

    private ConfigPersist() {
    }

    /**
     * 注册钩子：在 {@code ConfigRegistry.register} 之前把 spec 交给本类，返回要显式传入的文件名。
     *
     * @return Forge 惯例的显式文件名
     */
    /** 一份要落盘的 (spec, 文件, 标签)。注册顺序即保存顺序。 */
    private record Target(ForgeConfigSpec spec, Path file, String label) {
    }

    private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();

    public static String record(ModConfig.Type type, ForgeConfigSpec spec) {
        String name = fileName(type);
        Path file = FabricLoader.getInstance().getConfigDir().resolve(name);
        switch (type) {
            case CLIENT -> {
                clientFile = file;
                clientSpec = spec;
            }
            case COMMON -> {
                commonFile = file;
                commonSpec = spec;
            }
            default -> {
                // SERVER 配置由（集成）服务器生命周期持有，Cloth 面板从不编辑它。
            }
        }
        if (spec != null) {
            TARGETS.add(new Target(spec, file, type.toString().toLowerCase(Locale.ROOT)));
        }
        return name;
    }

    /**
     * 非标准命名的那份配置也要落盘：{@code PreLoadConfig} 的 {@code tacz-pre.toml} 里装着
     * {@code DefaultPackDebug}，而 Cloth 面板 {@code OtherClothConfig} 编辑的正是它 —— 不在本表里，
     * 那个开关就永远写不回去（"重启后配置回默认"在本线的第二个具体来源）。
     *
     * @param fileName 已经钉死的注册文件名（与 {@code ConfigRegistry.register} 的第 4 参同一个值）
     */
    public static String recordNamed(ForgeConfigSpec spec, String fileName) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        TARGETS.add(new Target(spec, file, fileName));
        return fileName;
    }

    /**
     * 把 CLIENT 与 COMMON 两份 spec 落盘。单个文件失败只记日志并跳过，不连带拖垮另一份。
     */
    public static void saveAll() {
        // 两条旧字段仍保留（供外部按类型单独取用），但落盘以 TARGETS 为准：
        // 它包含 PreLoadConfig 这类非标准命名的注册。
        if (TARGETS.isEmpty()) {
            save(clientSpec, clientFile, "client");
            save(commonSpec, commonFile, "common");
            return;
        }
        for (Target t : TARGETS) {
            save(t.spec(), t.file(), t.label());
        }
    }

    private static void save(@Nullable ForgeConfigSpec spec, @Nullable Path file, String label) {
        if (spec == null || file == null) {
            return;
        }
        try {
            Config child = ((ForgeConfigSpecAccessor) (Object) spec).tacz$getChildConfig();
            if (child == null) {
                // 【以前这里是静默 return】spec 还没被 FCAP 载进来时，面板改的值只存在于内存，
                // 我们无配置可写 ⇒ 必须说一声，否则"配置不持久化"与"FCAP 没加载"两种病看起来一模一样。
                GunMod.LOGGER.warn("[TACZ] Could not persist the {} config: its spec has no in-memory "
                        + "config yet (FCAP has not loaded it). Values stay effective this session only.",
                        label);
                return;
            }
            if (child instanceof FileConfig fileConfig) {
                // 旧架构：它自己会写（autosave/FileConfig 都在），交回给它，保留它的注释与格式。
                fileConfig.save();
                return;
            }
            if (Files.exists(file)) {
                // 先把值并进已有文件再存：CommentedFileConfig 会保留原文件的注释、键顺序与
                // 未被子句覆盖的条目；直接 TomlWriter 覆盖会把用户文件里的注释整片抹掉。
                CommentedFileConfig merged = CommentedFileConfig.builder(file)
                        .preserveInsertionOrder()
                        .build();
                try {
                    merged.load();
                    copyInto(child, merged, java.util.Collections.emptyList());
                    merged.save();
                } finally {
                    merged.close();
                }
                return;
            }
            // 文件还不存在（首启或被删）：新建，走原子替换。
            writeAtomic(child, file);
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] Failed to persist the {} config after saving it. The values stay "
                    + "effective for this session but will revert to the file's contents on next launch.", label, t);
        }
    }

    /** 递归把内存配置的叶子值写进目标（路径列表 → 值），中间表自动创建。 */
    private static void copyInto(Config from, CommentedConfig to, List<String> prefix) {
        for (Map.Entry<String, Object> e : from.valueMap().entrySet()) {
            List<String> path = new ArrayList<>(prefix.size() + 1);
            path.addAll(prefix);
            path.add(e.getKey());
            Object v = e.getValue();
            if (v instanceof Config sub) {
                copyInto(sub, to, path);
            } else {
                to.set(path, v);
            }
        }
    }

    /**
     * 原子写：先写同目录临时文件再 ATOMIC_MOVE 替换。
     *
     * <p>原来直接对目标文件 {@code newBufferedWriter} 会<b>先截断</b>：这一刻若 JVM 被杀/掉电，
     * 留下的是空文件 —— 下次启动读回"什么都没有" = 全部配置回到默认，而且再也找不回来。
     * 配置不持久化最坏的那种形态就是它。</p>
     */
    private static void writeAtomic(Config child, Path file) throws java.io.IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            new TomlWriter().write(child, writer);
        }
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
