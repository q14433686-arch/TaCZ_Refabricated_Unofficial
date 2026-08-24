package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;

/**
 * TacZ Mesh Loader 整合入口。
 *
 * <p>功能：让 TACZ 能加载 Blockbench Meshy 插件导出的 {@code poly_mesh}
 * 网格几何（枪包 display 写 {@code "model_type": "mesh"} 即启用）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class TaczMeshyIntegration {

    private TaczMeshyIntegration() {
    }

    public static void onClientSetup() {
        TaczPolyMeshGunModel.register();
    }
}
