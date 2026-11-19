package com.tacz.guns.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** CPU extraction workload. Same camera, scene and sample window, excluding setup, submit and GPU time. */
public final class RackLoadSmoke {
    private static Set<BlockPos> measured = Set.of();
    private static final Set<BlockPos> seen = new HashSet<>();
    private static final List<Long> elapsed = new ArrayList<>();
    private static long started;

    // The gametest mixin calls these on the render thread only.
    public static void start(BlockPos pos) {
        if (measured.contains(pos)) {
            seen.add(pos);
            started = System.nanoTime();
        }
    }

    public static void end() {
        if (started != 0) {
            elapsed.add(System.nanoTime() - started);
            started = 0;
        }
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var origin = ctx.computeOnClient(mc -> mc.player.blockPosition().offset(7, 1, 14));
        var positions = new HashSet<BlockPos>();
        for (int i = 0; i < 16; i++) positions.add(origin.west(i));
        world.getServer().runOnServer(s -> {
            var level = world.getConnection().getServerPlayer().level();
            if (!positions.stream().allMatch(level::isEmptyBlock)) throw new AssertionError("Stress fixture would overwrite the world");
        });
        ctx.getInput().lookAt(origin.west(7));
        for (String mode : List.of("same", "different")) {
            try {
                world.getServer().runOnServer(s -> {
                    var level = world.getConnection().getServerPlayer().level();
                    var source = s.createCommandSourceStack().withLevel(level).withRotation(Vec2.ZERO).withSuppressedOutput();
                    try {
                        var command = "tacz debug pegboard 64 %s %d %d %d".formatted(mode, origin.getX(), origin.getY(), origin.getZ());
                        int count = s.getCommands().getDispatcher().execute(command, source);
                        if (count != 64) throw new AssertionError("Expected 64 items, got " + count);
                    } catch (CommandSyntaxException e) {
                        throw new AssertionError("Stress fixture command rejected", e);
                    }
                });
                ctx.waitFor(mc -> positions.stream().allMatch(p -> mc.level.getBlockEntity(p) instanceof GunPegboardBlockEntity board
                        && !board.getItem(11).isEmpty()));
                world.getConnection().waitForChunksRender();
                ctx.waitTicks(20);
                ctx.runOnClient(mc -> measured = Set.copyOf(positions));
                ctx.waitTicks(30);
                ctx.runOnClient(mc -> {
                    measured = Set.of();
                    if (!seen.equals(positions)) throw new AssertionError("Only extracted " + seen.size() + " of 16 boards");
                    elapsed.sort(Long::compare);
                    GunMod.LOGGER.info("PORT_RACK_LOAD: 64 {} items, {} board extractions, CPU median={} us p95={} us", mode,
                            elapsed.size(), elapsed.get(elapsed.size() / 2) / 1000, elapsed.get(95 * elapsed.size() / 100) / 1000);
                });
                ctx.takeScreenshot("tacz_rack_load_" + mode);
            } finally {
                ctx.runOnClient(mc -> { measured = Set.of(); seen.clear(); elapsed.clear(); started = 0; });
                world.getServer().runOnServer(s -> {
                    var level = world.getConnection().getServerPlayer().level();
                    for (var pos : positions) {
                        if (level.getBlockEntity(pos) instanceof GunPegboardBlockEntity rack) {
                            rack.clearContent();
                            level.removeBlock(pos, false);
                        }
                    }
                });
                ctx.waitFor(mc -> positions.stream().allMatch(mc.level::isEmptyBlock));
            }
        }
    }
}
