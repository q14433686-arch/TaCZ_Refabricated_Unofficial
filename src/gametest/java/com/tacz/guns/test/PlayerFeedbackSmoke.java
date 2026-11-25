package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.concurrent.atomic.AtomicInteger;

/** Observe actual item submissions, ordinary damage and S2C delivery. The range controls use
 * one connected player, not a simulated second player or a long-distance projectile. */
public final class PlayerFeedbackSmoke {
    public static final AtomicInteger impactPackets = new AtomicInteger(), incendiaryPackets = new AtomicInteger();
    private static int offhand, hotbar, tilts;
    private static double expectedMultiplier;
    private static boolean observing;

    public static void backSubmit(int salt) {
        if (!observing) return;
        if (salt == 100) offhand++; else hotbar++;
    }

    public static void tilt(float angle, float direction, double multiplier, float result) {
        if (!observing || Math.abs(angle) < .0001f) return;
        float expected = angle * (float)expectedMultiplier * (1 - .5f * Math.abs((float)Math.sin(Math.toRadians(direction))));
        if (multiplier != expectedMultiplier || Math.abs(expected - result) > .0001f)
            throw new AssertionError("Ordinary hurt tilt " + result + ", expected " + expected + " at multiplier " + expectedMultiplier);
        tilts++;
    }

    private record Loadout(GameType mode, int slot, ItemStack zero, ItemStack one, ItemStack offhand) {
        static Loadout snapshot(ServerPlayer p) {
            var inv = p.getInventory();
            return new Loadout(p.gameMode.getGameModeForPlayer(), inv.getSelectedSlot(), inv.getItem(0).copy(),
                    inv.getItem(1).copy(), p.getOffhandItem().copy());
        }
        void restore(ServerPlayer p) {
            select(p, slot);
            p.getInventory().setItem(0, zero); p.getInventory().setItem(1, one);
            p.setItemInHand(InteractionHand.OFF_HAND, offhand);
            p.inventoryMenu.broadcastChanges();
            p.setGameMode(mode);
        }
    }

    private static void select(ServerPlayer p, int slot) {
        p.getInventory().setSelectedSlot(slot);
        p.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        boolean hidden = ctx.computeOnClient(mc -> RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get());
        double multiplier = ctx.computeOnClient(mc -> RenderConfig.GUN_HURT_BOB_MULTIPLIER.get());
        ClientProbe.waitForServer(ctx, world, s -> s.getPlayerList().getPlayers().getFirst().connection.hasClientLoaded());
        var server = world.getServer();
        var previous = server.computeOnServer(s -> Loadout.snapshot(s.getPlayerList().getPlayers().getFirst()));
        try {
            server.runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                p.setGameMode(GameType.SURVIVAL);
                select(p, 1);
                var gun = GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).build();
                p.setItemInHand(InteractionHand.MAIN_HAND, gun);
                p.setItemInHand(InteractionHand.OFF_HAND, gun.copy());
                p.getInventory().setItem(0, gun.copy());
                p.inventoryMenu.broadcastChanges();
            });
            ctx.waitFor(mc -> !mc.player.isCreative() && mc.player.getInventory().getSelectedSlot() == 1
                    && mc.player.getMainHandItem().getItem() instanceof IGun && mc.player.getOffhandItem().getItem() instanceof IGun);
            ctx.runOnClient(mc -> {
                observing = true; offhand = hotbar = tilts = 0;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(false);
            });
            ctx.waitFor(mc -> offhand > 0 && hotbar > 0);
            ctx.runOnClient(mc -> { RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(true); offhand = hotbar = 0; });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                if (offhand != 0 || hotbar != 0) throw new AssertionError("Hidden back-mounted gun still submitted");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            for (double configured : new double[]{0, .37, 1}) {
                ctx.waitTicks(21); // allow normal damage immunity to expire; do not edit the timer
                ctx.runOnClient(mc -> { RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(configured); expectedMultiplier = configured; tilts = 0; });
                server.runOnServer(s -> {
                    var p = s.getPlayerList().getPlayers().getFirst();
                    if (!p.hurtServer(p.level(), p.damageSources().generic(), 1)) throw new AssertionError("Ordinary damage refused");
                });
                ctx.waitFor(mc -> tilts > 0); // requires a real, nonzero vanilla rotation as input
            }
            checkImpactPackets(ctx, world);
            GunMod.LOGGER.info("PORT_FEEDBACK: back submissions toggle, ordinary hurt multipliers 0/.37/1 and five same-level delivery controls passed");
        } finally {
            ctx.runOnClient(mc -> {
                observing = false; mc.options.setCameraType(camera);
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(hidden);
                RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(multiplier);
            });
            server.runOnServer(s -> previous.restore(s.getPlayerList().getPlayers().getFirst()));
        }
    }

    private static void checkImpactPackets(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ClientProbe.waitForServer(ctx, world, s -> ServerPlayNetworking.canSend(s.getPlayerList().getPlayers().getFirst(), ServerMessageBulletHitBlock.Incendiary.TYPE));
        var server = world.getServer();
        int saved = server.computeOnServer(s -> SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.get());
        try {
            // Out-of-range shooter, unrelated far/near observer, unlimited range, near incendiary.
            for (int scenario = 0; scenario < 5; scenario++) {
                int mode = scenario, before = impactPackets.get(), hot = incendiaryPackets.get();
                server.runOnServer(s -> {
                    var p = s.getPlayerList().getPlayers().getFirst();
                    SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.set(mode == 3 ? 0 : 8);
                    var hit = p.position().add(mode == 2 || mode == 4 ? 4 : 20, 0, 0);
                    new ServerMessageBulletHitBlock(hit, Direction.UP, BlockPos.containing(hit), "tacz:762x39", "tacz:ak47", "tacz:ak47")
                            .broadcast(p.level(), mode == 0 ? p : null, mode == 4);
                });
                if (mode != 1) ctx.waitFor(mc -> impactPackets.get() > before);
                ctx.waitTicks(3);
                if (impactPackets.get() != before + (mode == 1 ? 0 : 1) || incendiaryPackets.get() != hot + (mode == 4 ? 1 : 0))
                    throw new AssertionError("Wrong impact delivery/fragment flag for scenario " + mode);
            }
        } finally { server.runOnServer(s -> SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.set(saved)); }
    }
}
