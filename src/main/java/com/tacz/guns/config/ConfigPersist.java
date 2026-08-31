package com.tacz.guns.config;

import com.electronwill.nightconfig.core.Config;
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
 *   <li>于是没有任何环节把改后的值写回 TOML：重启读回旧文件 = 「配置重置」。
 *       1.21.11（FCAP v21.11.1）与 26.2（v26.2.1）无此病 —— 它们还是旧架构/已修桥。</li>
 * </ol>
 * FCAP 自带的正规保存函数 {@code LoadedConfig.save()} 不可达：{@code LoadedConfig} 与
 * {@code ModConfig.loadedConfig} 都是包私有（编译实录，第一个修复提交被 CI 拒）。
 *
 * <h2>修法（本类）</h2>
 * 注册时用带文件名的 {@code register} 重载把文件钉在 Forge 惯例名
 * （{@code tacz-client.toml} / {@code tacz-common.toml}，与 FCAP 默认命名一致）；
 * Cloth 的保存流程最后一步（{@code ConfigBuilder#setSavingRunnable}）调
 * {@link #saveAll()}：Accessor 取出 {@code childConfig}（set() 已把新值写进去），
 * {@code TomlWriter} 显式写回注册名对应的 TOML —— 注释保留（SynchronizedConfig
 * 是带注释解析的）。SERVER 配置是世界生命周期所有物、面板不编辑，不在此处理。
 */
public final class ConfigPersist {
    /** Forge/FCAP 的默认命名惯例：{@code <modid>-<type>.toml}（显式注册，防默认规则变动）。 */
    private static String fileName(ModConfig.Type type) {
        return GunMod.MOD_ID + "-" + type.toString().toLowerCase(java.util.Locale.ROOT) + ".toml";
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
     * Registration hook: called from {@code TaCZFabric#onInitialize} before each
     * {@code ConfigRegistry.register} for the specs the Cloth screen can edit.
     *
     * @return the explicit file name to pass into that {@code register} overload.
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
                // SERVER configs are owned by the (integrated) server lifecycle and are never
                // edited from the Cloth screen; they keep FCAP's own save path.
            }
        }
        return name;
    }

    /**
     * Persists the CLIENT and COMMON specs after the Cloth screen saved its entries.
     * Each failure is logged and skipped so one broken file cannot take the other down.
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
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new TomlWriter().write(child, writer);
            }
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] Failed to persist the {} config after the Cloth screen saved it.", label, t);
        }
    }
}
