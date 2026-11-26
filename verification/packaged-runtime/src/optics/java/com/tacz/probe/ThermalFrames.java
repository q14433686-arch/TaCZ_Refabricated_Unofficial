package com.tacz.probe;

import com.google.gson.Gson;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.render.scope.ScopeLensFrame;
import com.tacz.guns.client.render.scope.ThermalReadback;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Real posed entities, real private heat attachment AND final lens pixels. The temporary
 * lens is injected into the loaded model only, never distributed as modified default-pack data. */
final class ThermalFrames {
    static void verify(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, Identifier optic) {
        var config = new Gson().fromJson("{\"vision_mode\":\"THERMAL_WHITE_HOT\",\"vision_noise\":0,\"distortion\":0,\"aberration\":0,\"vignette_strength\":0,\"tint_strength\":0}", ScopeLensConfig.class);
        config.init();
        var original = ctx.computeOnClient(mc -> {
            try {
                var model = TimelessAPI.getClientAttachmentIndex(optic).orElseThrow().getAttachmentModel();
                var field = com.tacz.guns.client.model.BedrockAttachmentModel.class.getDeclaredField("scopeLens");
                field.setAccessible(true);
                return (ScopeLensConfig) field.get(model);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        boolean pip = ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_ENABLE.get());
        boolean experimental = ctx.computeOnClient(mc -> RenderConfig.EXPERIMENTAL_SCOPE_LENS.get());
        boolean enabled = ctx.computeOnClient(mc -> RenderConfig.SCOPE_THERMAL_ENABLE.get());
        boolean rerender = ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_RERENDER.get());
        try {
            ctx.runOnClient(mc -> {
                TimelessAPI.getClientAttachmentIndex(optic).orElseThrow().getAttachmentModel().setScopeLens(config);
                RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(true);
                RenderConfig.SCOPE_THERMAL_ENABLE.set(true);
                RenderConfig.SCOPE_PIP_ENABLE.set(false);
                RenderConfig.SCOPE_PIP_RERENDER.set(false);
            });
            ctx.waitTicks(8);
            var empty = ThermalReadback.read(ctx);
            if (empty.hot() != 0) throw new AssertionError("Empty thermal lane has heat: " + empty);
            summon(ctx, world, base, "slime", "Size:2,NoAI:1b,NoGravity:1b,Silent:1b");
            var exposed = ThermalReadback.read(ctx);
            var hot = frame(ctx,"thermal_exposed");
            if (exposed.centralHot() < 20 || white(hot) < 10) throw new AssertionError("Missing translucent posed heat / sensor pixels: " + exposed + " white=" + white(hot)
                    + " diagnostic-only:" + ThermalReadback.diagnoseDepth(ctx));
            wall(ctx,world,base,1);
            var covered = ThermalReadback.read(ctx);
            var behind = frame(ctx,"thermal_occluded");
            if (covered.hot() != 0 || white(behind) > 4) throw new AssertionError("Heat through opaque wall: " + covered + " white=" + white(behind));
            wall(ctx,world,base,2);
            var glass = ThermalReadback.read(ctx);
            if (glass.centralHot() < 20) throw new AssertionError("Transparent occluder lost visible heat: " + glass);
            frame(ctx,"thermal_glass");
            wall(ctx,world,base,0);
            ctx.runOnClient(mc -> RenderConfig.SCOPE_THERMAL_ENABLE.set(false));
            ctx.waitTicks(6);
            ctx.runOnClient(mc -> ThermalReadback.assertCleared());
            var optical = frame(ctx,"thermal_disabled");
            if (white(optical) >= white(hot)/2) throw new AssertionError("Disabling heat did not restore the optical lens");
            ctx.runOnClient(mc -> RenderConfig.SCOPE_THERMAL_ENABLE.set(true));
            ctx.waitTicks(6);
            if (ThermalReadback.read(ctx).centralHot() < 20) throw new AssertionError("Heat did not recover after disable");
            kill(ctx,world);
            summon(ctx,world,base,"chicken","NoAI:1b,NoGravity:1b,Silent:1b");
            var chicken=ThermalReadback.read(ctx);
            if (chicken.centralHot()<3) throw new AssertionError("Missing cutout chicken heat: "+chicken);
            kill(ctx,world);
            summon(ctx,world,base,"chicken","NoAI:1b,NoGravity:1b,Silent:1b");
            world.getServer().runCommand("effect give @e[tag=thermal_probe] minecraft:invisibility infinite 0 true");
            ctx.waitTicks(6);
            var invisible=ThermalReadback.read(ctx);
            if(invisible.hot()!=0) throw new AssertionError("Invisible entity heated: "+invisible);
            kill(ctx,world);
            summon(ctx,world,base,"armor_stand","NoGravity:1b");
            var blacklisted=ThermalReadback.read(ctx);
            if(blacklisted.hot()!=0) throw new AssertionError("Blacklist failed: "+blacklisted);
            kill(ctx,world);
            summon(ctx,world,base,"minecart","NoGravity:1b");
            var whitelisted=ThermalReadback.read(ctx);
            if(whitelisted.centralHot()<3) throw new AssertionError("Nonliving whitelist failed: "+whitelisted);
            ctx.runOnClient(mc -> ThermalReadback.releaseAndVerify());
            ctx.waitTicks(8);
            if (ThermalReadback.read(ctx).centralHot()<3) throw new AssertionError("Heat failed to reallocate after resource cleanup");
            System.out.println("PORT_THERMAL_PIXELS: empty="+empty.hot()+" exposed="+exposed.centralHot()+" sensorWhite="+white(hot)
                    +" opaque="+covered.hot()+" opaqueWhite="+white(behind)+" glass="+glass.centralHot()+" chicken="+chicken.centralHot()
                    +" invisible="+invisible.hot()+" blacklist="+blacklisted.hot()+" whitelist="+whitelisted.centralHot()+" optical fallback and recovery passed");
        } finally {
            kill(ctx,world);
            wall(ctx,world,base,0);
            ctx.runOnClient(mc -> {
                TimelessAPI.getClientAttachmentIndex(optic).orElseThrow().getAttachmentModel().setScopeLens(original);
                RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(experimental);
                RenderConfig.SCOPE_THERMAL_ENABLE.set(enabled);
                RenderConfig.SCOPE_PIP_ENABLE.set(pip);
                RenderConfig.SCOPE_PIP_RERENDER.set(rerender);
                ScopeLensFrame.clear();
            });
        }
    }
    private static void summon(ClientGameTestContext ctx,TestSingleplayerContext world,BlockPos base,String type,String nbt) {
        world.getServer().runCommand("summon minecraft:"+type+" "+(base.getX()+.5)+" "+(base.getY()+1.2)+" "+(base.getZ()+16.5)+" {Tags:[\"thermal_probe\"],"+nbt+"}");
        ctx.waitTicks(12);
    }
    private static void kill(ClientGameTestContext ctx,TestSingleplayerContext world) {
        world.getServer().runCommand("kill @e[tag=thermal_probe]");
        ctx.waitTicks(25); // living death animation must be gone, not an occluding corpse
    }
    private static void wall(ClientGameTestContext ctx,TestSingleplayerContext world,BlockPos base,int mode) {
        var block=mode==0?Blocks.AIR:mode==1?Blocks.STONE:Blocks.GLASS;
        var mid=base.south(8);
        world.getServer().runOnServer(s->{var level=s.getPlayerList().getPlayers().getFirst().level();
            for(int y=-2;y<=6;y++) for(int x=-4;x<=4;x++) level.setBlock(mid.offset(x,y,0),block.defaultBlockState(),Block.UPDATE_ALL);
        });
        ctx.waitFor(mc->mc.level.getBlockState(mid).is(block));
        world.getClientWorld().waitForChunksRender(); ctx.waitTicks(6);
    }
    private static BufferedImage frame(ClientGameTestContext ctx,String name) {
        ctx.waitTicks(4);
        try { return ImageIO.read(ctx.takeScreenshot("tacz_"+name).toFile()); }
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static int white(BufferedImage frame) {
        int w=frame.getWidth(),h=frame.getHeight(),r=h/14,n=0;
        for(int y=h/2-r;y<h/2+r;y++) for(int x=w/2-r;x<w/2+r;x++) {
            int p=frame.getRGB(x,y),red=p>>16&255,g=p>>8&255,b=p&255;
            if(Math.min(red,Math.min(g,b))>180 && Math.max(red,Math.max(g,b))-Math.min(red,Math.min(g,b))<15)n++;
        }
        return n;
    }
    private ThermalFrames(){}
}
