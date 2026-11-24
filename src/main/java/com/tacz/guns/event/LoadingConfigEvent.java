package com.tacz.guns.event;

import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import net.neoforged.fml.config.ModConfig;

public final class LoadingConfigEvent {
    private LoadingConfigEvent() {}

    public static void onLoadingConfig(ModConfig config) {
        ConfigPersist.track(config);
        // FCAP fires synchronously during register(). Both pre and common are COMMON;
        // the pre event fires before CommonConfig.init() has even constructed its values.
        switch (config.getFileName()) {
            case "tacz-common.toml" -> AmmoBlockConfigRead.init();
            case "tacz-server.toml" -> {
                HeadShotAABBConfigRead.init();
                InteractKeyConfigRead.init();
                GunPegboardItemConfigRead.init();
            }
        }
    }

    public static void onReloadingConfig(ModConfig config) { onLoadingConfig(config); }
}
