package com.tacz.probe;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Exercise the production admission gate independently from LRU capacity; no GPU draw is faked. */
final class PlatformMeshBudget {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            int capacity=MeshyConfig.GPU_LIGHT_CACHE_SIZE.get(),budget=MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.get();
            try {
                var field=PolyMeshGpuRenderer.class.getDeclaredField("bakesThisFrame");field.setAccessible(true);
                int saved=field.getInt(null);
                try {
                    for(int cache:new int[]{1,16})for(int allowed:new int[]{1,2,7}) {
                        MeshyConfig.GPU_LIGHT_CACHE_SIZE.set(cache);MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.set(allowed);field.setInt(null,0);
                        for(int i=0;i<allowed;i++)if(!PolyMeshGpuRenderer.tryReserveBake())throw new AssertionError("Bake budget under-admitted at cache="+cache+", budget="+allowed);
                        if(PolyMeshGpuRenderer.tryReserveBake() || PolyMeshGpuRenderer.tryReserveBake(cache))throw new AssertionError("LRU capacity overrode per-frame bake budget");
                    }
                } finally {field.setInt(null,saved);}
            } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
            finally {MeshyConfig.GPU_LIGHT_CACHE_SIZE.set(capacity);MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.set(budget);}
        });
        System.out.println("PORT_PLATFORM_MESH_BUDGET: native bake admission honored budgets 1/2/7 independently of LRU capacities 1/16; legacy entrypoint used the same gate");
    }
    private PlatformMeshBudget(){}
}
