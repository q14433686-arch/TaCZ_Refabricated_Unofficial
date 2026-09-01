package com.tacz.guns.config;

import com.electronwill.nightconfig.core.Config;
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
import java.util.Locale;

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
 * <p>调用点：Cloth 面板 {@code ConfigBuilder#setSavingRunnable} 的最后一步 + {@code /tacz config}
 * 命令改值之后。两处都是"用户明确要求保存"的时刻，不逐帧写盘。</p>
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
        return name;
    }

    /**
     * 把 CLIENT 与 COMMON 两份 spec 落盘。单个文件失败只记日志并跳过，不连带拖垮另一份。
     */
    public static void saveAll() {
        save(clientSpec, clientFile, "client");
        save(commonSpec, commonFile, "common");
    }

    private static void save(@Nullable ForgeConfigSpec spec, @Nullable Path file, String label) {
        if (spec == null || file == null || !spec.isLoaded()) {
            return;
        }
        try {
            Config child = ((ForgeConfigSpecAccessor) (Object) spec).tacz$getChildConfig();
            if (child == null) {
                return;
            }
            if (child instanceof FileConfig) {
                // 旧架构：spec 自己会写（autosave/FileConfig 都在），交给它，避免我们绕过它的注释策略。
                spec.save();
                return;
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new TomlWriter().write(child, writer);
            }
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] Failed to persist the {} config after saving it. The values stay "
                    + "effective for this session but will revert to the file's contents on next launch.", label, t);
        }
    }
}
