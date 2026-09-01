package com.tacz.guns.compat.cloth;

import com.tacz.guns.compat.cloth.client.*;
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
        // 【FCAP 26.x 保存断桥】各 entry 的 saveConsumer 只写内存（ConfigValue.set
        // 不落盘、ForgeConfigSpec.save() 在新架构下恒 no-op），必须在保存流程的
        // 最后一步显式写回 TOML —— 否则每次重启配置回到旧文件值（26.1.2 线先发病，
        // 26.2 线同病；本线 L-21/L-22 已修，但 ModMenu 那条路径此前没接）。详见 ConfigPersist。
        // 必须放在 getConfigBuilder() 里（而不是 getConfigScreen()），否则 ModMenu
        // 直接走 getConfigBuilder().build() 会绕过这条保存链 —— "重启后配置回默认"的
        // 最后一个可见出口。
        root.setSavingRunnable(com.tacz.guns.config.ConfigPersist::saveAll);
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
