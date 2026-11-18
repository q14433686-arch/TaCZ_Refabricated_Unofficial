package com.tacz.guns.test;

import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer;
import com.tacz.guns.client.particle.bedrock.particle.runtime.*;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

final class SnowstormSmoke {
    private static final String DEFINITION="""
      {"particle_effect":{"description":{"identifier":"port_test:snowstorm","basic_render_parameters":{
       "material":"particles_add","texture":"minecraft:textures/particle/generic_0"}},
       "components":{
       "minecraft:emitter_lifetime_once":{"active_time":0.2},
       "minecraft:emitter_rate_instant":{"num_particles":"variable.count"},
       "minecraft:emitter_shape_point":{"offset":[0,0,0]},
       "minecraft:particle_lifetime_expression":{"max_lifetime":0.6},
       "minecraft:particle_initial_speed":0,
       "minecraft:particle_appearance_billboard":{"size":[0.12,0.12],"facing_camera_mode":"rotate_xyz"},
       "minecraft:particle_appearance_tinting":{"color":[1,0,1,1]}
      }}}
      """;
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        ctx.runOnClient(mc->{
            if(com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader.getInstance()
                    .getDefinition(net.minecraft.resources.Identifier.parse("port_acceptance:runtime_probe"))==null)
                throw new AssertionError("External-pack particle definition was not loaded");
            var compiler=new ParticleMolangEnvironment();
            var def=new ParticleEffectDeserializer().parse(JsonParser.parseString(DEFINITION),compiler);
            var a=new ParticleMolangEnvironment();a.eval("variable.count = 3;");
            var b=new ParticleMolangEnvironment();b.eval("variable.count = 7;");
            var first=new ParticleEmitterInstance(def,a);var second=new ParticleEmitterInstance(def,b);
            first.tick(.05f);second.tick(.05f);
            if(first.getParticleCount()!=3||second.getParticleCount()!=7)throw new AssertionError("Compiled Molang context leaked between emitters");
            if(Math.abs(a.eval("math.sin(90) + math.pow(2,3)")-9)>1e-6)throw new AssertionError("Molang math failed");
            for(int i=0;i<20;i++){first.tick(.05f);second.tick(.05f);}
            if(!first.isFinished()||!second.isFinished())throw new AssertionError("Emitter/particle lifetime not collected");
            var camera=mc.gameRenderer.mainCamera();var pos=camera.position().add(mc.player.getLookAngle().scale(2));
            WorldEmitterManager.getInstance().addEmitter(mc.level,new Matrix4f().translation((float)pos.x,(float)pos.y,(float)pos.z),Vec3.ZERO,def,"variable.count = 4;");
        });
        long before=ctx.computeOnClient(mc->WorldEmitterManager.getInstance().rendered());
        ctx.waitTicks(3);
        if(ctx.computeOnClient(mc->WorldEmitterManager.getInstance().rendered())<=before)throw new AssertionError("Snowstorm did not submit actual render geometry");
        try {
            var image=javax.imageio.ImageIO.read(ctx.takeScreenshot("tacz_snowstorm_runtime").toFile());
            int pixels=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int color=image.getRGB(x,y),r=(color>>16)&255,g=(color>>8)&255,b=color&255;
                if(r>100&&b>100&&r>g*1.5&&b>g*1.5)pixels++;
            }
            if(pixels<8)throw new AssertionError("Submitted Snowstorm geometry was not visibly magenta: "+pixels);
            GunMod.LOGGER.info("PORT_SNOWSTORM_PIXELS: {} magenta pixels in actual client frame",pixels);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        ctx.waitTicks(25);
        if(ctx.computeOnClient(mc->WorldEmitterManager.getInstance().emitterCount())!=0)throw new AssertionError("Snowstorm retained expired emitters");
        GunMod.LOGGER.info("PORT_SNOWSTORM: compiled Molang math, isolated variables, pre-effect count, lifetimes and real render geometry passed");
    }
}
