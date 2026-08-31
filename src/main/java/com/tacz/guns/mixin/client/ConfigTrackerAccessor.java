package com.tacz.guns.mixin.client;

import net.neoforged.fml.config.ConfigTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 ForgeConfigAPIPort 的 {@code ConfigTracker} 单例与按 mod 分组的配置表，
 * 供 {@code ConfigPersist} 在 Cloth 保存后定位本 mod 的 {@code ModConfig}。
 *
 * <p>背景（配置重置 bug，26.1.2 独有）：FCAP v26.1.5 的 {@code ConfigValue.set} 只写
 * 内存 {@code childConfig}，而 {@code ForgeConfigSpec.save()} 只认 NightConfig
 * {@code FileConfig} —— 新架构加载的是内存 {@code SynchronizedConfig}，{@code save()}
 * 恒为静默 no-op，玩家改的配置永远不落盘。修复 = 保存后走 {@code LoadedConfig#save()}
 * （FCAP 自己的正规写盘函数）。字段存在性由 CI javap 探针核实（compile-java.log，
 * 2026-09-01）：{@code INSTANCE}（putstatic @938）、{@code configsByMod}（@234）。</p>
 */
@Mixin(ConfigTracker.class)
public interface ConfigTrackerAccessor {

    @Accessor("INSTANCE")
    static ConfigTracker tacz$getInstance() {
        throw new AssertionError("mixin accessor");
    }

    @Accessor("configsByMod")
    java.util.Map<String, java.util.List<net.neoforged.fml.config.ModConfig>> tacz$getConfigsByMod();
}
