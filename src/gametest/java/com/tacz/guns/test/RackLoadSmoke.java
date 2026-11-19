package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import java.util.*;

/** Small repeatable workload, not a GPU benchmark. Time CPU extraction of actual visible BEs
 * in the same client/world/camera; exclude loading frames, command setup, submit and GPU time. */
public final class RackLoadSmoke {
    private static Set<BlockPos> measured = Set.of();
    private static long started;
    private static final ArrayList<Long> elapsed = new ArrayList<>();
    public static void start(BlockPos pos) { started = measured.contains(pos) ? System.nanoTime() : 0; }
    public static void end() { if (started != 0) { elapsed.add(System.nanoTime()-started); started=0; } }
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world) {
        var origin = ctx.computeOnClient(mc -> mc.player.blockPosition().offset(7,1,14));
        var positions = new HashSet<BlockPos>(); for(int i=0;i<16;++i)positions.add(origin.west(i));
        world.getServer().runOnServer(s->{var l=world.getConnection().getServerPlayer().level();
            if(!positions.stream().allMatch(l::isEmptyBlock))throw new AssertionError("Stress fixture would overwrite the world");});
        ctx.getInput().lookAt(origin.west(7));
        for(String mode:List.of("same","different")) {
            try {
                world.getServer().runOnServer(s->{var l=world.getConnection().getServerPlayer().level();
                    var source=s.createCommandSourceStack().withLevel(l).withRotation(Vec2.ZERO).withSuppressedOutput();
                    int n;
                    try { n=s.getCommands().getDispatcher().execute("tacz debug pegboard 64 "+mode+" "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),source); }
                    catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new AssertionError("Stress fixture command rejected",ex);}
                    if(n!=64)throw new AssertionError("Expected 64 displayed items, got "+n);});
                ctx.waitFor(mc -> positions.stream().allMatch(p -> mc.level.getBlockEntity(p) instanceof GunPegboardBlockEntity b && !b.getItem(11).isEmpty()));
                world.getConnection().waitForChunksRender();ctx.waitTicks(20);
                ctx.runOnClient(mc->{elapsed.clear();measured=Set.copyOf(positions);});ctx.waitTicks(30);
                ctx.runOnClient(mc->{measured=Set.of();if(elapsed.size()<16)throw new AssertionError("No visible rack extraction workload");
                    elapsed.sort(Long::compare);GunMod.LOGGER.info("PORT_RACK_LOAD: 64 {} items, {} visible board extractions, CPU median={} us p95={} us",mode,
                            elapsed.size(),elapsed.get(elapsed.size()/2)/1000,elapsed.get(95*elapsed.size()/100)/1000);});
            } finally {
                ctx.runOnClient(mc->{measured=Set.of();elapsed.clear();});
                world.getServer().runOnServer(s->{var l=world.getConnection().getServerPlayer().level();for(var p:positions)if(l.getBlockEntity(p) instanceof GunPegboardBlockEntity b){b.clearContent();l.removeBlock(p,false);}});
                ctx.waitFor(mc->positions.stream().allMatch(mc.level::isEmptyBlock));
            }
        }
    }
}
