package com.tacz.guns.config;

import com.tacz.guns.GunMod;
import com.tacz.guns.mixin.client.ConfigTrackerAccessor;
import com.tacz.guns.mixin.client.ModConfigAccessor;
import net.neoforged.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

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
 *
 * <h2>修法</h2>
 * Cloth 的保存流程最后会执行 {@code ConfigBuilder#setSavingRunnable} 的回调；
 * 这里对 CLIENT/COMMON 两条配置找到 FCAP 实际使用的 {@code ModConfig}，取出其
 * {@code loadedConfig} 并调 {@link net.neoforged.fml.config.LoadedConfig#save()} ——
 * 这是 FCAP 自己的正规保存函数（写 {@code path()}、锁内触发配置重载事件），
 * 文件路径由 FCAP 管理，零猜测。注册处的 spec 引用由 {@code TaCZFabric} 传入。
 */
public final class ConfigPersist {
    @Nullable
    private static ForgeConfigSpec clientSpec;
    @Nullable
    private static ForgeConfigSpec commonSpec;

    private ConfigPersist() {
    }

    /** Registration hook: called from {@code TaCZFabric#onInitialize} for each spec we register. */
    public static void record(ModConfig.Type type, ForgeConfigSpec spec) {
        switch (type) {
            case CLIENT -> clientSpec = spec;
            case COMMON -> commonSpec = spec;
            default -> {
                // SERVER configs are owned by the (integrated) server lifecycle and are never
                // edited from the Cloth screen; they keep FCAP's own save path.
            }
        }
    }

    /**
     * Persists every spec this mod registered through FCAP whose values the Cloth screen can
     * edit (CLIENT + COMMON). Safe to call on any thread the Cloth save flow runs on; each
     * failure is logged and skipped so one broken file cannot take the others down.
     */
    public static void saveAll() {
        Map<String, List<ModConfig>> byMod = ((ConfigTrackerAccessor) (Object) ConfigTrackerAccessor.tacz$getInstance())
                .tacz$getConfigsByMod();
        List<ModConfig> ours = byMod == null ? null : byMod.get(GunMod.MOD_ID);
        if (ours == null) {
            return;
        }
        for (ModConfig config : ours) {
            ForgeConfigSpec spec = switch (config.getType()) {
                case CLIENT -> clientSpec;
                case COMMON -> commonSpec;
                default -> null;
            };
            if (spec == null) {
                continue;
            }
            try {
                var loaded = ((ModConfigAccessor) config).tacz$getLoadedConfig();
                if (loaded != null) {
                    loaded.save();
                }
            } catch (Throwable t) {
                GunMod.LOGGER.warn("[TACZ] Failed to persist {} config after the Cloth screen saved it.",
                        config.getType(), t);
            }
        }
    }
}
