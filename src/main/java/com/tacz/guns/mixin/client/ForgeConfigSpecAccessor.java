package com.tacz.guns.mixin.client;

import com.electronwill.nightconfig.core.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 FCAP 的 {@code ForgeConfigSpec.childConfig}（nightconfig 的内存对象）。
 *
 * <p>背景（「重启后配置回到默认」，根因全文见 {@link com.tacz.guns.config.ConfigPersist}）：
 * {@code ConfigValue.set()} 只写这份内存配置，而 {@code ForgeConfigSpec.save()} 只在
 * {@code childConfig instanceof FileConfig} 时落盘 —— 新架构下该 instanceof 永假 ⇒ save() 是
 * <b>静默</b> no-op，面板/命令改的值重启即丢。修复就是取出本字段后用 {@code TomlWriter} 显式写回。</p>
 *
 * <p>{@code remap = false}：FCAP 是 mod 类、不在 Minecraft 的映射表里；本线开着 legacy mixin AP
 * 要生成 refmap（26.1.2 那条线把 AP 整块关了，所以那边不需要这层），显式关掉重映射最稳。</p>
 */
@Mixin(value = ForgeConfigSpec.class, remap = false)
public interface ForgeConfigSpecAccessor {

    @Accessor("childConfig")
    Config tacz$getChildConfig();
}
