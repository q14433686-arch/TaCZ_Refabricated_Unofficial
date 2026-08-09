package com.tacz.guns.config;

import com.tacz.guns.config.client.*;
import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        KeyConfig.init(builder);
        RenderConfig.init(builder);
        ResourceConfig.init(builder);
        SoundConfig.init(builder);
        ZoomConfig.init(builder);
        // 内置附属 TacZ Mesh Loader 的客户端配置（见 docs/MESH_LOADER_INTEGRATION_PLAN.md）
        cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig.init(builder);
        return builder.build();
    }
}
