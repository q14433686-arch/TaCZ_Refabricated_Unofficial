package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import cn.sh1rocu.tacz.compat.meshloader.render.ShaderStateTracker;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * TacZ Mesh Loader 整合入口。
 *
 * <p>在客户端 setup 时注册：{@code model_type: "mesh"} 枪模构造器、
 * geo 解析缓存失效监听器，以及两个状态追踪基建
 * （{@link ScreenRenderTracker} 精确 GUI 渲染瞬间、
 * {@link ShaderStateTracker} Iris 开关态切换）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class TaczMeshyIntegration {

    private TaczMeshyIntegration() {
    }

    public static void onClientSetup() {
        TaczPolyMeshGunModel.register();
        registerReloadListener();
        // 状态追踪基建：第 0 步铺好，第 1 步 GPU 静态烘焙直接消费。
        ScreenRenderTracker.register();
        ShaderStateTracker.register();
    }

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
