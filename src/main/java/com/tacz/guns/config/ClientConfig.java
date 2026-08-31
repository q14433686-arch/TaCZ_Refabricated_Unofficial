package com.tacz.guns.config;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
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
        // 内置 TacZ Mesh Loader（poly_mesh / GPU 静态烘焙）的 18 项客户端配置，
        // 挂在本 spec 的 mesh_loader 段下；与 RenderClothConfig / 语言键三方齐平
        // （docs/check_mesh_config_parity.py）。
        MeshyConfig.init(builder);
        return builder.build();
    }
}
