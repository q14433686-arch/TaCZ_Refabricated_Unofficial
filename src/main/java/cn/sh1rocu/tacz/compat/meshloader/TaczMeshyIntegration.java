package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
        registerReloadListener();
    }

    /**
     * 客户端资源重载时失效 poly_mesh 解析缓存。
     *
     * <p>geo JSON 内容可能随枪包增删而变，缓存的 {@code PolyMesh} 解析结果
     * 不能跨资源代际复用。注册方式逐行对照
     * {@code ClientAssetsManager.reloadAndRegister} 里那个匿名监听器
     * （同一注册口、同一 {@code reload(...)} 形态）。</p>
     */
    private static void registerReloadListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            static final Identifier ID = Identifier.fromNamespaceAndPath("tacz", "poly_mesh_parse_cache");

            @Override
            public Identifier getFabricId() {
                return ID;
            }

            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                                  PreparationBarrier barrier, Executor gameExecutor) {
                return barrier.wait(null).thenRunAsync(PolyMeshSupport::invalidateParseCache, gameExecutor);
            }
        });
    }
}
