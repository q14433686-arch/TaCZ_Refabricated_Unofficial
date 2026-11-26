package com.tacz.guns.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

public final class PackClientSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var id = Identifier.parse("tacz:ak47");
        var loading = new java.util.concurrent.atomic.AtomicBoolean(true);
        long start = System.nanoTime();
        int[] ticks = {0};
        // Observe the pre-context failure too: worldBuilder.create() can time out before
        // any assertions run. Do not retry, lengthen Fabric's limit or suppress the failure.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!loading.get() || ++ticks[0] % 200 != 1) return;
            var server = mc.getSingleplayerServer();
            System.out.println("PORT_WORLD_LOAD_DIAGNOSTIC: ticks="+ticks[0]+" elapsedMs="+(System.nanoTime()-start)/1_000_000
                    +" screen="+(mc.screen==null ? "none" : mc.screen.getClass().getName()+":"+mc.screen.getTitle().getString())
                    +" overlay="+(mc.getOverlay()==null ? "none" : mc.getOverlay().getClass().getName())
                    +" level="+(mc.level!=null)+" player="+(mc.player!=null)+" integratedTicks="+(server==null ? -1 : server.getTickCount()));
        });
        try (var world = context.worldBuilder().create()) {
            loading.set(false);
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null && TimelessAPI.getClientGunIndex(id).isPresent());
            ConversionReloadSmoke.run(context, world);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
        finally { loading.set(false); }
    }
}
