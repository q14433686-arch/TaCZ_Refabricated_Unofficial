package com.tacz.guns.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tacz.guns.GunMod;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 26.2 重构版 PreLoadConfig
 * <p>
 * 旧实现: 继承 {@code ModConfig} 然后从 {@code ConfigTracker} 中 hack 移除
 * <p>
 * 26.2.x 问题: {@code net.neoforged.fml.config.ModConfig} 构造函数是 package-private,
 * 无法被子类调用 {@code super(type, spec, modId, fileName)}
 * <p>
 * 新实现: 直接用 nightconfig 加载自定义路径的配置文件, 通过 {@link ConfigRegistry} 注册 ForgeConfigSpec
 * 用于配置事件通知. 不再 hack ConfigTracker.
 */
public class PreLoadConfig {
    public static ForgeConfigSpec spec;
    public static ForgeConfigSpec.BooleanValue override;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("gunpack");
        builder.comment("When enabled, the mod will not try to overwrite the default pack under .minecraft/tacz\n" +
                "Since 1.0.4, the overwriting will only run when you start client or a dedicated server");
        override = builder.define("DefaultPackDebug", false);
        builder.pop();
        spec = builder.build();
    }

    public static void init() {
        // 注册到 ConfigRegistry (触发正常的 ModConfigEvents)
        // 文件名交给 ConfigPersist 钉：它同时把这份 spec 记进落盘表 —— DefaultPackDebug
        // 是 Cloth 面板 OtherClothConfig 会改的键，不记就永远写不回 tacz-pre.toml。
        ConfigRegistry.INSTANCE.register(GunMod.MOD_ID, ModConfig.Type.COMMON, spec,
                ConfigPersist.recordNamed(spec, "tacz-pre.toml"));
    }

    /**
     * 26.2 重构: 直接用 nightconfig 加载, 不再走 ModConfig 继承
     *
     * @param configBasePath 配置文件基础路径
     */
    public static void load(Path configBasePath) {
        if (spec.isLoaded()) return;

        Path configFile = configBasePath.resolve("tacz-pre.toml");
        CommentedFileConfig configData = CommentedFileConfig.builder(configFile)
                .autosave()
                .preserveInsertionOrder()
                .build();
        configData.load();

        // 解析到 spec
        spec.acceptConfig(configData);
    }
}
