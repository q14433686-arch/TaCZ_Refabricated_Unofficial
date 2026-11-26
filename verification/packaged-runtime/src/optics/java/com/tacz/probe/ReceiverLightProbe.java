package com.tacz.probe;

import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Dedicated real-world receiver launch. Keep all optical regressions in their own launches,
 * not in front of each receiver iteration (especially expensive with software Iris rendering). */
public final class ReceiverLightProbe extends PlayerProbe {
    @Override void exercise(ClientGameTestContext ctx,TestSingleplayerContext world) {
        var server=world.getServer();
        var base=server.computeOnServer(s -> {
            var p=s.getPlayerList().getPlayers().getFirst();
            var at=new BlockPos(p.blockPosition().getX(),240,p.blockPosition().getZ());
            p.level().setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());return at;
        });
        server.runCommand("weather clear");
        server.runCommand("tp @a "+(base.getX()+.5)+" "+base.getY()+" "+(base.getZ()+.5));
        ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(base));
        ReceiverLightSmoke.verify(ctx,world,base);
        boolean expected=System.getProperty("tacz.probe.player").endsWith("-iris");
        boolean actual=ctx.computeOnClient(mc -> IrisCompat.isUsingRenderPack());
        if(expected!=actual) throw new AssertionError("Receiver shader state "+actual+" expected="+expected);
        System.out.println("PORT_RECEIVER_PACK: Iris active="+actual);
    }
}
