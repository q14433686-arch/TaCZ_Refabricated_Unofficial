package com.tacz.guns.compat.cloth;

import com.tacz.guns.compat.cloth.client.*;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.compat.cloth.common.AmmoClothConfig;
import com.tacz.guns.compat.cloth.common.GunClothConfig;
import com.tacz.guns.compat.cloth.common.OtherClothConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public class MenuIntegration {
    public static ConfigBuilder getConfigBuilder() {
        ConfigBuilder root = ConfigBuilder.create().setTitle(Component.literal("Timeless and Classics Guns"));
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        // 保存流程最后一步显式落盘：FCAP v26.1.5 的 ConfigValue.set 只改内存、
        // ForgeConfigSpec.save() 在新架构下是 no-op，不在这里补 LoadedConfig.save()
        // 的话玩家改的配置重启即丢（详见 ConfigPersist 的根因分析）。
        root.setSavingRunnable(ConfigPersist::saveAll);
        ConfigEntryBuilder entryBuilder = root.entryBuilder();

        KeyClothConfig.init(root, entryBuilder);
        RenderClothConfig.init(root, entryBuilder);
        ResourceClothConfig.init(root, entryBuilder);
        SoundClothConfig.init(root, entryBuilder);
        ZoomClothConfig.init(root, entryBuilder);

        GunClothConfig.init(root, entryBuilder);
        AmmoClothConfig.init(root, entryBuilder);
        OtherClothConfig.init(root, entryBuilder);

        return root;
    }

    public static Screen getConfigScreen(@Nullable Screen parent) {
        return MenuIntegration.getConfigBuilder().setParentScreen(parent).build();
    }
}
