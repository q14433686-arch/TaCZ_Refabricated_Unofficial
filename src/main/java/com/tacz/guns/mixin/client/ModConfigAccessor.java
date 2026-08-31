package com.tacz.guns.mixin.client;

import net.neoforged.fml.config.LoadedConfig;
import net.neoforged.fml.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 FCAP 的 {@code ModConfig.loadedConfig}（record：config + path + modConfig）。
 *
 * <p>其 {@code LoadedConfig#save()} 是 FCAP/NeoForge 自己的正规「保存配置」函数：
 * {@code path != null} 时 {@code ConfigTracker.writeConfig(path, config)} 落盘，
 * 并在锁内触发 {@code ModConfigEventsHelper.onReloading}。配置重置 bug 的修复正是
 * 在 Cloth 保存后对 CLIENT/COMMON 调它 —— 见 {@code ConfigPersist}。
 * 字段名由 CI javap 探针核实（compile-java.log，2026-09-01）：
 * {@code acceptSyncedConfig} @0 {@code getfield loadedConfig}。</p>
 */
@Mixin(ModConfig.class)
public interface ModConfigAccessor {

    @Accessor("loadedConfig")
    LoadedConfig tacz$getLoadedConfig();
}
