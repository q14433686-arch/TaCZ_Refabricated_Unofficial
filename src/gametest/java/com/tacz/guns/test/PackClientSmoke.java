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
        long[] start = {System.nanoTime()};
        int[] ticks = {0}, cycle = {1};
        // Observe the pre-context failure too: worldBuilder.create() can time out before
        // any assertions run. Do not retry, lengthen Fabric's limit or suppress the failure.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!loading.get() || ++ticks[0] % 200 != 1) return;
            var server = mc.getSingleplayerServer();
            System.out.println("PORT_WORLD_LOAD_DIAGNOSTIC: cycle="+cycle[0]+" ticks="+ticks[0]+" elapsedMs="+(System.nanoTime()-start[0])/1_000_000
                    +" screen="+(mc.screen==null ? "none" : mc.screen.getClass().getName()+":"+mc.screen.getTitle().getString())
                    +" overlay="+(mc.getOverlay()==null ? "none" : mc.getOverlay().getClass().getName())
                    +" level="+(mc.level!=null)+" player="+(mc.player!=null)+" integratedTicks="+(server==null ? -1 : server.getTickCount()));
            if (ticks[0] == 1001 && server != null && server.getTickCount() == 0) {
                // The observed server is waiting in Fabric's phase barrier, not a pack worker.
                // Record its party/accounting state without mutating or bypassing the barrier.
                try {
                    var threading = Class.forName("net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl");
                    var phaser = (java.util.concurrent.Phaser)threading.getField("PHASER").get(null);
                    System.out.println("PORT_WORLD_STALL: FabricPhase phase="+phaser.getPhase()+" registered="+phaser.getRegisteredParties()
                            +" arrived="+phaser.getArrivedParties()+" unarrived="+phaser.getUnarrivedParties()
                            +" serverRunning="+threading.getField("isServerRunning").get(null)
                            +" serverTasks="+threading.getField("serverCanAcceptTasks").get(null));
                } catch (ReflectiveOperationException ex) { System.out.println("PORT_WORLD_STALL: FabricPhase diagnostic unavailable: "+ex); }
                Thread.getAllStackTraces().entrySet().stream()
                        .filter(e -> e.getKey().getName().equals("Server thread") || e.getKey().getName().contains("Worker") || e.getKey().getName().contains("ForkJoinPool"))
                        .sorted(java.util.Comparator.comparing(e -> e.getKey().getName().equals("Server thread") ? "" : e.getKey().getName()))
                        .limit(12).forEach(e -> System.out.println("PORT_WORLD_STALL: "+e.getKey().getName()+" "+e.getKey().getState()+" "
                                +java.util.Arrays.stream(e.getValue()).limit(18).map(Object::toString).collect(java.util.stream.Collectors.joining(" <- "))));
            }
        });
        try (var world = context.worldBuilder().create()) {
            loading.set(false);
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null && TimelessAPI.getClientGunIndex(id).isPresent());
            ConversionReloadSmoke.run(context, world);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
        finally { loading.set(false); }
        // Three independent fresh-world lifecycles are a stability gate, NOT retries:
        // any failure immediately fails this test. Each keeps Fabric's original 1200-tick limit.
        for (int n = 2; n <= 3; n++) {
            int number = n;
            // An integrated memory connection deliberately shares the common cache with its
            // server (CommonNetworkCacheEvent.onLocalLogout). Only TCP teardown promises an
            // empty cache. Require actual disconnect here and a NEW snapshot on the next join,
            // rather than waiting for a memory-connection invariant that must never hold.
            context.waitFor(mc -> mc.level == null && mc.getConnection() == null);
            var previous = context.computeOnClient(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id));
            context.runOnClient(mc -> { ticks[0] = 0; cycle[0] = number; start[0] = System.nanoTime(); loading.set(true); });
            try (var world = context.worldBuilder().create()) {
                loading.set(false);
                context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null
                        && CommonNetworkCache.INSTANCE.getGunIndex(id) != previous
                        && TimelessAPI.getClientGunIndex(id).isPresent());
            } finally { loading.set(false); }
        }
        System.out.println("PORT_WORLD_REPEATED: three independent fresh worlds loaded, disconnected and replaced synchronized snapshots; no retries or timeout changes");
    }
}
