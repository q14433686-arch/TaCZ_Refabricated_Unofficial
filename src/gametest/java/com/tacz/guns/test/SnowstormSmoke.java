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
    private static final class EventAnimation extends com.tacz.guns.api.client.animation.ObjectAnimation {
        EventAnimation(String name){super(name);includeDuration(1);setParticleChannel(new com.tacz.guns.api.client.animation.ObjectAnimationParticleChannel(
            java.util.List.of(new com.tacz.guns.api.client.animation.ObjectAnimationParticleChannel.Keyframe(0,
                java.util.List.of(new com.tacz.guns.api.client.animation.AnimationParticleEvent(net.minecraft.resources.Identifier.parse("port_acceptance:runtime_probe"),"muzzle_flash","variable.count = 3;"))))));}
    }
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        ctx.runOnClient(mc->{
            if(com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader.getInstance()
                    .getDefinition(net.minecraft.resources.Identifier.parse("port_acceptance:runtime_probe"))==null)
                throw new AssertionError("External-pack particle definition was not loaded");
            var controller=new com.tacz.guns.api.client.animation.AnimationController(java.util.List.of(new EventAnimation("source"),new EventAnimation("target")),(node,type)->null);
            controller.runAnimation(0,"source",com.tacz.guns.api.client.animation.ObjectAnimation.PlayType.PLAY_ONCE_STOP,0);
            controller.runAnimation(0,"target",com.tacz.guns.api.client.animation.ObjectAnimation.PlayType.PLAY_ONCE_STOP,.2f);
            controller.update();
            if(controller.consumeParticleEvents().size()!=1||!controller.consumeParticleEvents().isEmpty())
                throw new AssertionError("Transition target particle was dropped or consumed twice");
            var compiler=new ParticleMolangEnvironment();
            var def=new ParticleEffectDeserializer().parse(JsonParser.parseString(DEFINITION),compiler);
            var curveJson=JsonParser.parseString(DEFINITION).getAsJsonObject();
            curveJson.getAsJsonObject("particle_effect").add("curves",JsonParser.parseString("""
              {"variable.test_curve":{"type":"linear","input":0.5,"horizontal_range":1,"nodes":[0,2]}}
              """));
            var curveDef=new ParticleEffectDeserializer().parse(curveJson,compiler);
            var curveEnv=new ParticleMolangEnvironment();curveEnv.eval("variable.count = 1;");
            var curveEmitter=new ParticleEmitterInstance(curveDef,curveEnv);curveEmitter.tick(.05f);
            if(Math.abs(curveEnv.eval("variable.test_curve")-1)>1e-6)throw new AssertionError("Curve variable namespace mapping failed");
            if(Math.abs(curveEnv.compile("query.delta_time").evaluate(curveEnv.getContext())-.05)>1e-6)throw new AssertionError("Particle query binding failed");
            var a=new ParticleMolangEnvironment();a.eval("variable.count = 3;");
            var b=new ParticleMolangEnvironment();b.eval("variable.count = 7;");
            var first=new ParticleEmitterInstance(def,a);var second=new ParticleEmitterInstance(def,b);
            first.tick(.05f);second.tick(.05f);
            if(first.getParticleCount()!=3||second.getParticleCount()!=7)throw new AssertionError("Compiled Molang context leaked between emitters");
            var shortJson=JsonParser.parseString(DEFINITION).getAsJsonObject();
            shortJson.getAsJsonObject("particle_effect").getAsJsonObject("components")
                    .getAsJsonObject("minecraft:particle_lifetime_expression").addProperty("max_lifetime",.02);
            var shortEnv=new ParticleMolangEnvironment();shortEnv.eval("variable.count = 1;");
            var shortEmitter=new ParticleEmitterInstance(new ParticleEffectDeserializer().parse(shortJson,compiler),shortEnv);
            shortEmitter.tick(.05f);
            if(shortEmitter.getParticleCount()!=1||shortEmitter.getParticles().getFirst().age!=0)
                throw new AssertionError("Sub-tick flash died before its first render opportunity");
            shortEmitter.tick(.05f);
            if(shortEmitter.getParticleCount()!=0)throw new AssertionError("Sub-tick flash did not expire next tick");
            if(Math.abs(a.eval("math.sin(90) + math.pow(2,3)")-9)>1e-6)throw new AssertionError("Molang math failed");
            for(int i=0;i<20;i++){first.tick(.05f);second.tick(.05f);}
            if(!first.isFinished()||!second.isFinished())throw new AssertionError("Emitter/particle lifetime not collected");
            // The child runtime_probe emits variable.count particles (zero in its fresh environment).
            // Its pre-effect must instead see and update the top-level initializer's parent value.
            var rt=WorldEmitterManager.getInstance();rt.clear();
            var nested=JsonParser.parseString(DEFINITION).getAsJsonObject();
            var effect=nested.getAsJsonObject("particle_effect");
            effect.getAsJsonObject("components").add("minecraft:emitter_lifetime_events",JsonParser.parseString("{\"creation_event\":\"child\"}"));
            effect.add("events", JsonParser.parseString("""
                {"child":{"particle_effect":{"effect":"port_acceptance:runtime_probe","type":"emitter",
                "pre_effect_expression":"variable.count = variable.count + 3;"}}}
                """));
            var parent=rt.addEmitter(mc.level,new Matrix4f(),Vec3.ZERO,new ParticleEffectDeserializer().parse(nested,compiler),"variable.count = 2;");
            if(parent==null || rt.emitterCount()!=2 || parent.getMolang().eval("variable.count")!=5)throw new AssertionError("Nested expression did not use parent context");
            rt.tick();
            if(parent.getParticleCount()!=5 || rt.particleCount()!=5)throw new AssertionError("Nested expression leaked into child: parent="+parent.getParticleCount()+", total="+rt.particleCount());
            rt.clear();
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
        SnowstormMaterialSmoke.run(ctx, world);
        GunMod.LOGGER.info("PORT_SNOWSTORM: compiled Molang math, isolated variables, pre-effect count, lifetimes and real render geometry passed");
    }
}
