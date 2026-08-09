package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;

/**
 * TacZ Mesh Loader 整合入口（当前为 P0+P1：枪械 poly_mesh 支持）。
 *
 * <p>功能：让 TACZ 能加载 Blockbench Meshy 插件导出的 {@code poly_mesh}
 * 网格几何（枪包 display 写 {@code "model_type": "mesh"} 即启用）。
 * 26.2 版按 {@code SubmitNodeCollector} 延迟渲染管线重写，
 * 见 {@code docs/MESH_LOADER_INTEGRATION_PLAN.md}。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class TaczMeshyIntegration {

    private TaczMeshyIntegration() {
    }

    /** 由 {@code TaCZFabricClient#onInitializeClient} 调用，只注册一次。 */
    public static void onClientSetup() {
        TaczPolyMeshGunModel.register();
    }
}
