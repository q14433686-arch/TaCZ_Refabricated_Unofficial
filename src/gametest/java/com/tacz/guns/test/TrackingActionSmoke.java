package com.tacz.guns.test;

import cn.sh1rocu.tacz.TaCZFabric;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.network.message.event.ServerMessageGunActionPost;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** One client tracking a distinct server actor; no local prediction can produce these observations. */
final class TrackingActionSmoke {
    private static volatile UUID watched;
    private static boolean cancel; // only read/written on the server thread
    private static final AtomicInteger serverPosts = new AtomicInteger(), clientPosts = new AtomicInteger();
    private static final AtomicInteger clientPre = new AtomicInteger(), clientFinishes = new AtomicInteger();
    static {
        GunShootEvent.CALLBACK.register(e -> {
            if (e.getLogicalSide().isServer() && e.getShooter().getUUID().equals(watched) && cancel) e.setCanceled(true);
        });
        GunFireEvent.CALLBACK.register(TaCZFabric.LOWEST, e -> {
            if (!e.getShooter().getUUID().equals(watched)) return;
            if (e.getLogicalSide().isClient()) clientPre.incrementAndGet();
            else if (cancel) e.setCanceled(true);
        });
        GunFirePostEvent.EVENT.register(e -> {
            if (!e.getShooter().getUUID().equals(watched)) return;
            var stack = e.getGunItemStack();
            if (!(stack.getItem() instanceof IGun gun) || gun.getCurrentAmmoCount(stack) != 1)
                throw new AssertionError("Observer received a pre-debit stack");
            (e.getLogicalSide().isClient() ? clientPosts : serverPosts).incrementAndGet();
        });
        GunFinishReloadEvent.EVENT.register(e -> {
            if (e.getLogicalSide().isClient() && e.getEntity() != null && e.getEntity().getUUID().equals(watched))
                clientFinishes.incrementAndGet();
        });
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        var mob = server.computeOnServer(s -> {
            var player = world.getConnection().getServerPlayer();
            var cow = new Cow(EntityTypes.COW, player.level());
            cow.snapTo(player.position().add(3, 0, 0));
            cow.setNoAi(true);
            cow.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .setAmmoCount(2).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
            player.level().addFreshEntity(cow);
            IGunOperator.fromLivingEntity(cow).initialData();
            return cow;
        });
        watched = mob.getUUID();
        clientPre.set(0); serverPosts.set(0); clientPosts.set(0); clientFinishes.set(0);
        var type = ServerMessageGunActionPost.TYPE;
        try {
            world.getConnection().waitForClientboundEntityUpdates(EntityTypes.COW);
            context.waitFor(mc -> mc.level.getEntity(watched) instanceof Cow);
            server.waitFor(s -> PlayerLookup.tracking(mob).contains(world.getConnection().getServerPlayer())
                    && ServerPlayNetworking.canSend(world.getConnection().getServerPlayer(), type));
            server.runOnServer(s -> {
                var held = mob.getMainHandItem();
                var gun = (IGun) held.getItem();
                var api = ScriptGuardSmoke.bind(mob);
                api.setPitchSupplier(() -> -90f);
                cancel = true;
                try {
                    gun.setBulletInBarrel(held, false);
                    var operator = IGunOperator.fromLivingEntity(mob);
                    // Start outside the configured cooldown window; the network timestamp still comes from the operator.
                    operator.getDataHolder().baseTimestamp = com.tacz.guns.util.time.GunTime.nowMillis() - 5000;
                    if (operator.shoot(() -> -90f, () -> 0f) != ShootResult.FORGE_EVENT_CANCEL
                            || gun.hasBulletInBarrel(held) || gun.getCurrentAmmoCount(held) != 2)
                        throw new AssertionError("Cancelled trigger changed chamber or magazine");
                    gun.setBulletInBarrel(held, true);
                    api.shootOnce(true);
                } finally { cancel = false; }
                if (gun.getCurrentAmmoCount(held) != 2 || serverPosts.get() != 0) throw new AssertionError("Cancelled cycle settled");
                gun.setCurrentAmmoCount(held, 0);
                gun.setBulletInBarrel(held, false);
                api.shootOnce(true);
                if (serverPosts.get() != 0) throw new AssertionError("Unfunded cycle settled");
                gun.setCurrentAmmoCount(held, 2);
                gun.setBulletInBarrel(held, true);
                api.shootOnce(true);
                // Complete a reload on the server tick path, not by calling a packet handler directly.
                var data = IGunOperator.fromLivingEntity(mob).getDataHolder();
                data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FINISHING;
                data.reloadTimestamp = 0;
            });
            context.waitFor(mc -> clientFinishes.get() >= 1 && clientPosts.get() >= 1);
            if (serverPosts.get() != 1 || clientPosts.get() != 1 || clientFinishes.get() != 1)
                throw new AssertionError("Tracking client received extra success events");
            checkMissingPostChannel(context, world, mob);
            GunMod.LOGGER.info("PORT_ACTION: tracking client observed settled fire/reload once; legacy pre remained with post channel withdrawn");
        } finally {
            watched = null;
            server.runOnServer(s -> mob.discard());
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkMissingPostChannel(ClientGameTestContext context, TestSingleplayerContext world, Cow mob) {
        var type = ServerMessageGunActionPost.TYPE;
        var server = world.getServer();
        // Only this connection's receiver is changed. Wait for the actual Fabric capability update on the server.
        var previous = context.computeOnClient(mc -> (ClientPlayNetworking.PlayPayloadHandler<ServerMessageGunActionPost>)
                ClientPlayNetworking.unregisterReceiver(type.id()));
        if (previous == null) throw new AssertionError("Missing post receiver at test setup");
        try {
            server.waitFor(s -> !ServerPlayNetworking.canSend(world.getConnection().getServerPlayer(), type));
            int before = clientPre.get();
            server.runOnServer(s -> {
                var held = mob.getMainHandItem();
                ((IGun) held.getItem()).setCurrentAmmoCount(held, 2);
                var api = ScriptGuardSmoke.bind(mob);
                api.setPitchSupplier(() -> -90f);
                api.shootOnce(true);
            });
            context.waitFor(mc -> clientPre.get() > before);
            if (serverPosts.get() != 2 || clientPosts.get() != 1) throw new AssertionError("Wrong post delivery without capability");
        } finally {
            context.runOnClient(mc -> {
                if (!ClientPlayNetworking.registerReceiver(type, previous)) throw new AssertionError("Failed to restore receiver");
            });
        }
        server.waitFor(s -> ServerPlayNetworking.canSend(world.getConnection().getServerPlayer(), type));
    }
}
