package cn.sh1rocu.tacz.compat.meshloader;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
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
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class TaczMeshyIntegration {

    private TaczMeshyIntegration() {
    }

    public static void onClientSetup() {
        TaczPolyMeshGunModel.register();
        // 世界 GPU 路径的 GUI 精确闸门（Screen extract 窗口探测），
        // 语义与上游 TML 的 ScreenRenderTracker 一致。
        cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker.register();
        registerReloadListener();
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
