package com.tacz.guns.test;

import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Same particle, texture (four authored opacity bands), camera and geometry. Only material and backing change. */
final class SnowstormMaterialSmoke {
    static final List<String> MATERIALS = List.of("particles_opaque", "particles_alpha", "particles_blend", "particles_add", "particles_emissive", "energy_swirl");
    static final String PROBE = """
        {"particle_effect":{"description":{"identifier":"port_test:material", "basic_render_parameters":{
         "material":"%s","texture":"port_test:particle/opacity_bands"}},"components":{
         "minecraft:emitter_rate_instant":{"num_particles":1},"minecraft:emitter_lifetime_once":{"active_time":0.1},
         "minecraft:emitter_shape_point":{"offset":[0,0,0]},"minecraft:particle_initial_speed":0,
         "minecraft:particle_lifetime_expression":{"max_lifetime":2},
         "minecraft:particle_appearance_billboard":{"size":[0.21,0.21],"facing_camera_mode":"rotate_xyz"},
         "minecraft:particle_appearance_tinting":{"color":[0.9,0.2,0.7,1]}}}}
        """;
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var rt = WorldEmitterManager.getInstance();
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        var wall = world.getServer().computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var pos = p.blockPosition().south(4).above();
            var states = new HashMap<BlockPos,BlockState>();
            for (int x=-1; x<=1; ++x) for (int y=-1; y<=1; ++y) {
                var at=pos.offset(x,y,0); states.put(at,p.level().getBlockState(at));
            }
            return states;
        });
        try {
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            ctx.getInput().lookAt(0,0); ctx.waitTicks(15);
            for (boolean dark : new boolean[]{true,false}) {
                var backing = dark ? Blocks.COAL_BLOCK : Blocks.SMOOTH_STONE;
                world.getServer().runOnServer(s -> wall.keySet().forEach(pos -> world.getConnection().getServerPlayer().level().setBlock(pos,backing.defaultBlockState(),Block.UPDATE_ALL)));
                ctx.waitFor(mc -> wall.keySet().stream().allMatch(pos -> mc.level.getBlockState(pos).is(backing)));
                world.getConnection().waitForChunksRender(); ctx.waitTicks(3);
                var prefix = "tacz_material_" + (dark ? "dark_" : "light_");
                var base = read(ctx.takeScreenshot(prefix + "control"));
                if (dark) {
                    ParticlePassSmoke.run(ctx, base);
                    BoneLightSmoke.run(ctx, base);
                    SnowstormSmoke.checkRuntimePulse(ctx, base);
                }
                var images = new HashMap<String,BufferedImage>();
                for (String name : MATERIALS) {
                    ctx.runOnClient(mc -> {
                        var def = new ParticleEffectDeserializer().parse(JsonParser.parseString(PROBE.formatted(name)), new ParticleMolangEnvironment());
                        var p = mc.gameRenderer.mainCamera().position().add(mc.player.getLookAngle().scale(1.7));
                        rt.addEmitter(mc.level,new Matrix4f().translation((float)p.x,(float)p.y,(float)p.z),Vec3.ZERO,def,"");
                    });
                    ctx.waitTicks(3);
                    var image = read(ctx.takeScreenshot(prefix+name)); images.put(name,image);
                    int coverage = difference(base,image);
                    if (coverage < 80) throw new AssertionError(prefix+name+": only "+coverage+" pixels differ from empty wall");
                    ctx.runOnClient(mc -> rt.clear()); ctx.waitTicks(2);
                }
                int alphaVsBlend = difference(images.get("particles_alpha"),images.get("particles_blend"));
                int alphaVsCutout = difference(images.get("particles_alpha"),images.get("particles_opaque"));
                if (alphaVsCutout < 100 || alphaVsBlend > alphaVsCutout/3) throw new AssertionError(prefix+"alpha/blend="+alphaVsBlend+" alpha/cutout="+alphaVsCutout);
                GunMod.LOGGER.info("PORT_MATERIALS: {} alpha/blend={} alpha/cutout={}; all six materials visible", prefix,alphaVsBlend,alphaVsCutout);
                HandMaterialSmoke.run(ctx, world, dark ? "dark" : "light");
            }
            BillboardViewSmoke.run(ctx);
        } finally {
            ctx.runOnClient(mc -> rt.clear());
            world.getServer().runOnServer(s -> {var p=world.getConnection().getServerPlayer(); wall.forEach((pos,state)->p.level().setBlock(pos,state,Block.UPDATE_ALL)); p.setItemInHand(InteractionHand.MAIN_HAND,saved);});
        }
    }
    static BufferedImage read(Path path) {
        try { return ImageIO.read(path.toFile()); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static int difference(BufferedImage a, BufferedImage b) {
        int n=0, cx=a.getWidth()/2, cy=a.getHeight()/2;
        for(int y=cy-42;y<cy+42;y++)for(int x=cx-42;x<cx+42;x++) {
            if (Math.abs(x-cx)<7 && Math.abs(y-cy)<7) continue;
            int p=a.getRGB(x,y),q=b.getRGB(x,y);
            if (Math.abs((p>>16&255)-(q>>16&255))+Math.abs((p>>8&255)-(q>>8&255))+Math.abs((p&255)-(q&255))>16) n++;
        }
        return n;
    }
}
