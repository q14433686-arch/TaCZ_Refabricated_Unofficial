package com.tacz.guns.mixin.client;

import com.electronwill.nightconfig.core.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 FCAP 的 {@code ForgeConfigSpec.childConfig}（夜间配置的内存对象）。
 *
 * <p>背景（配置重置 bug，26.1.2 独有，CI javap 探针实读 compile-java.log 2026-09-01）：
 * FCAP v26.1.5 的 {@code ConfigValue.set} 只写这份内存配置，而 {@code ForgeConfigSpec.save()}
 * 只在 {@code childConfig instanceof FileConfig} 时落盘 —— 新架构（{@code LoadedConfig}，
 * TOML 解析进 {@code SynchronizedConfig}）下该 instanceof 永假 ⇒ save() 恒为静默 no-op，
 * Cloth 界面保存的值重启即丢。修复 = {@code ConfigPersist.saveAll()} 取出本字段后用
 * {@code TomlWriter} 显式写回 TOML。字段名 {@code childConfig} 与声明类型均由探针核实
 * （{@code private com.electronwill.nightconfig.core.Config childConfig}）。</p>
 */
@Mixin(ForgeConfigSpec.class)
public interface ForgeConfigSpecAccessor {

    @Accessor("childConfig")
    Config tacz$getChildConfig();
}
