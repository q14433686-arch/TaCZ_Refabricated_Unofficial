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
        var watchdog = new java.util.concurrent.atomic.AtomicLong();
        scheduleStallSnapshot(loading, watchdog, 1);
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
                System.out.println("PORT_WORLD_STALL: ServerState "+serverScalars(server)
                        +" connection="+(mc.getConnection()==null ? "none" : mc.getConnection().getClass().getName())
                        +" bootstrap="+bootstrapConnections(mc,server));
                // The observed server is waiting in Fabric's phase barrier, not a pack worker.
                // Record its party/accounting state without mutating or bypassing the barrier.
                try {
                    var threading = Class.forName("net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl");
                    var phaser = (java.util.concurrent.Phaser)threading.getField("PHASER").get(null);
                    System.out.println("PORT_WORLD_STALL: FabricPhase phase="+phaser.getPhase()+" registered="+phaser.getRegisteredParties()
                            +" arrived="+phaser.getArrivedParties()+" unarrived="+phaser.getUnarrivedParties()
                            +" serverRunning="+threading.getField("isServerRunning").get(null)
                            +" serverTasks="+threading.getField("serverCanAcceptTasks").get(null)
                            +" network="+networkScalars());
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
            scheduleStallSnapshot(loading, watchdog, number);
            try (var world = context.worldBuilder().create()) {
                loading.set(false);
                context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null
                        && CommonNetworkCache.INSTANCE.getGunIndex(id) != previous
                        && TimelessAPI.getClientGunIndex(id).isPresent());
            } finally { loading.set(false); }
        }
        System.out.println("PORT_WORLD_REPEATED: three independent fresh worlds loaded, disconnected and replaced synchronized snapshots; no retries or timeout changes");
    }

    /** Named development-test diagnostics only; never use field-name reflection in the remapped product.
     * getConnection() stays null throughout login and server tickCount stays zero while no player has joined.
     * Observe pending login transport as well, without changing network queues or Fabric's synchronizer. */
    private static String bootstrapConnections(Object client, net.minecraft.server.MinecraftServer server) {
        try {
            var pending = client.getClass().getDeclaredField("pendingConnection");pending.setAccessible(true);
            Object connection = pending.get(client);
            var listener = server.getConnection();
            var connections = listener.getClass().getDeclaredField("connections");connections.setAccessible(true);
            var result = new java.util.ArrayList<String>();
            result.add("pendingClient="+connectionDetails(connection));
            configurationDetails("client",connection);
            Object list = connections.get(listener);
            if (list instanceof java.util.List<?> values) {
                synchronized (values) { for (Object value : values) { result.add("server="+connectionDetails(value));configurationDetails("server",value); } }
            } else result.add("serverConnections="+list);
            return result.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) { return "unavailable:"+ex; }
    }

    private static void configurationDetails(String side,Object connection) throws ReflectiveOperationException {
        if(connection==null)return;
        var field=connection.getClass().getDeclaredField("packetListener");field.setAccessible(true);
        Object listener=field.get(connection);if(listener==null)return;
        var result=new java.util.ArrayList<String>();
        result.add(listener.getClass().getName());
        for(Class<?> type=listener.getClass();type!=null;type=type.getSuperclass())for(var f:type.getDeclaredFields()) {
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getName().toLowerCase(java.util.Locale.ROOT)
                    .matches(".*(task|registry|registries|pack|pending|finish|config|future).*"))continue;
            f.setAccessible(true);Object value=f.get(listener);
            result.add(f.getName()+"="+configurationValue(value));
        }
        System.out.println("PORT_WORLD_STALL: ConfigurationState "+side+"="+String.join(", ",result));
    }

    private static String configurationValue(Object value) {
        if(value==null)return "none";
        if(value instanceof java.util.Collection<?> values)return "size:"+values.size()+":"+values.stream().limit(8).map(PackClientSmoke::configurationValue).toList();
        if(value instanceof java.util.Map<?,?> values)return "size:"+values.size()+":keys="+values.keySet().stream().limit(8).map(String::valueOf).toList();
        if(value instanceof java.util.concurrent.Future<?> future)return value.getClass().getName()+":done="+future.isDone()+":cancelled="+future.isCancelled();
        if(value instanceof net.minecraft.server.network.ConfigurationTask task)return value.getClass().getName()+":type="+task.type();
        if(value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value instanceof Enum<?>)return String.valueOf(value);
        return value.getClass().getName();
    }

    private static String networkScalars() throws ReflectiveOperationException {
        var type=Class.forName("net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer");
        var result=new java.util.ArrayList<String>();
        for(String side:new String[]{"CLIENTBOUND","SERVERBOUND"}) {
            Object sync=type.getField(side).get(null);
            for(String name:new String[]{"inFlightPackets","mainThreadPacketHandlers","invalid","isRunningNetworkTasks"}) {
                var field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(sync);
                result.add(side+"."+name+"="+(value instanceof java.util.Collection<?> values ? values.size() : value));
            }
        }
        return result.toString();
    }

    private static String connectionDetails(Object connection) throws ReflectiveOperationException {
        if (connection == null) return "none";
        var result = new java.util.ArrayList<String>();
        for (var field : connection.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !field.getName().toLowerCase(java.util.Locale.ROOT).matches(".*(listener|channel|disconnect|queue|packet|pending).*")) continue;
            field.setAccessible(true);Object value=field.get(connection);
            result.add(field.getName()+"="+(value instanceof java.util.Collection<?> values ? "size:"+values.size()
                    : value == null || value instanceof Number || value instanceof Boolean || field.getName().equals("channel") ? value : value.getClass().getName()));
        }
        return result.toString();
    }

    /** Diagnostic-only scalar reads on the client tick; no task/barrier or server state changes. */
    private static String serverScalars(Object server) {
        var fields = new java.util.ArrayList<String>();
        for (Class<?> type = server.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()
                        || !field.getName().toLowerCase(java.util.Locale.ROOT).matches(".*(tick|ready|running|stopp|pause|load).*")) continue;
                try { field.setAccessible(true); fields.add(field.getName()+"="+field.get(server)); }
                catch (ReflectiveOperationException | RuntimeException ex) { fields.add(field.getName()+"=unavailable"); }
                if (fields.size() >= 20) return fields.toString();
            }
        }
        return fields.toString();
    }

    private static void scheduleStallSnapshot(java.util.concurrent.atomic.AtomicBoolean loading,
                                              java.util.concurrent.atomic.AtomicLong watchdog, int cycle) {
        long token = watchdog.incrementAndGet();
        java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            if (!loading.get() || watchdog.get() != token) return;
            // END_CLIENT_TICK always observes the server at a normal phase boundary. An
            // independent observer is needed to see which party is absent *between* ticks.
            // Never release a semaphore, arrive at the phaser, or call game-thread APIs here.
            try {
                var threading = Class.forName("net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl");
                var phaser = (java.util.concurrent.Phaser)threading.getField("PHASER").get(null);
                System.out.println("PORT_WORLD_STALL: ASYNC cycle="+cycle+" phase="+phaser.getPhase()+" registered="+phaser.getRegisteredParties()
                        +" arrived="+phaser.getArrivedParties()+" unarrived="+phaser.getUnarrivedParties()
                        +" serverTasks="+threading.getField("serverCanAcceptTasks").get(null)
                        +" clientTasks="+threading.getField("clientCanAcceptTasks").get(null));
                Thread.getAllStackTraces().entrySet().stream()
                        .filter(e -> e.getKey().getName().equals("Server thread") || e.getKey().getName().equals("Render thread")
                                || e.getKey().getName().equals("Test thread") || e.getKey().getName().contains("Netty"))
                        .sorted(java.util.Comparator.comparingInt(e -> switch (e.getKey().getName()) {
                            case "Server thread" -> 0;
                            case "Render thread" -> 1;
                            case "Test thread" -> 2;
                            default -> 3;
                        })).limit(4)
                        .forEach(e -> System.out.println("PORT_WORLD_STALL: ASYNC "+e.getKey().getName()+" "+e.getKey().getState()+" "
                                +java.util.Arrays.stream(e.getValue()).limit(24).map(Object::toString).collect(java.util.stream.Collectors.joining(" <- "))));
            } catch (ReflectiveOperationException ex) { System.out.println("PORT_WORLD_STALL: ASYNC diagnostic unavailable: "+ex); }
        });
    }
}
