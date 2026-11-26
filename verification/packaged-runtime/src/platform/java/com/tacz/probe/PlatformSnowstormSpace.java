package com.tacz.probe;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleDescription;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Audit the native event's real world/view split instead of inferring it from 26.x names. */
final class PlatformSnowstormSpace {
    private static boolean requested,done;
    private static Throwable failure;
    private static final List<String> receipts=new ArrayList<>();
    static void register(){
        WorldRenderEvents.BEFORE_ENTITIES.register(context->{
            if(!requested)return;
            requested=false;
            try {
                var mc=Minecraft.getInstance();var camera=context.worldState().cameraRenderState.pos;
                var manager=new WorldEmitterManager();PlatformAds.write(manager,"level",mc.level);
                var id=Identifier.parse("tacz_packaged_probe:space");
                var desc=new ParticleDescription(id,ParticleDescription.Material.PARTICLES_OPAQUE,Identifier.parse("minecraft:textures/block/gold_block.png"),16,16);
                var definition=new ParticleEffectDefinition(id,desc,List.of(),null,null);
                var emitter=manager.addEmitter(mc.level,Vec3.ZERO,Vec3.ZERO,definition);manager.tick();
                if(emitter==null)throw new AssertionError("No isolated native emitter fixture");
                var p=new ParticleInstance();p.emitter=emitter;p.worldSpace=true;p.maxLifetime=100;
                p.x=(float)camera.x+1;p.y=(float)camera.y+2;p.z=(float)camera.z+5;p.width=.3f;p.height=.2f;
                var position=new Vector3f(p.x,p.y,p.z);emitter.getParticles().add(p);
                var points=new ArrayList<Vector3f>();var tasks=new ArrayList<Runnable>();
                var collector=new SubmitNodeStorage(){@Override public void submitCustomGeometry(PoseStack pose,RenderType type,CustomGeometryRenderer draw){var frozen=pose.last().copy();tasks.add(()->draw.render(frozen,new Vertices(points)));}};
                var submit=WorldEmitterManager.class.getDeclaredMethod("submit",SubmitNodeCollector.class,Vec3.class);submit.setAccessible(true);
                submit.invoke(manager,collector,camera);
                p.x+=100;emitter.setEmitterTransform(new Matrix4f().translation(1000,2000,3000),new Matrix4f().translation(1000,2000,3000));
                tasks.forEach(Runnable::run);
                if(points.size()!=4)throw new AssertionError("Snowstorm fixture made "+points.size()+" vertices");
                var modelView=new Matrix4f(RenderSystem.getModelViewMatrix());
                var expected=new Matrix4f(modelView).mul(context.matrices().last().pose()).translate((float)-camera.x,(float)-camera.y,(float)-camera.z).transformPosition(position,new Vector3f());
                var actual=new Vector3f();points.forEach(actual::add);actual.div(4);modelView.transformPosition(actual);
                float error=actual.distance(expected);
                receipts.add("yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" centerError="+error+" rootIdentity="+context.matrices().last().pose().equals(new Matrix4f()));
                if(error>.0005f)throw new AssertionError("Native Snowstorm omitted/doubled world view transform: "+actual+" != "+expected+" "+receipts.getLast());
            } catch(Throwable e){failure=e;}
            finally {done=true;}
        });
    }
    static void verify(ClientGameTestContext ctx){
        var original=ctx.computeOnClient(mc->new float[]{mc.player.getYRot(),mc.player.getXRot()});
        try {
            for(float[] angles:new float[][]{{20,0},{87,20},{193,-25}}){
                PlayerProbe.turn(ctx,angles[0],angles[1]);ctx.waitTicks(2);
                ctx.runOnClient(mc->{failure=null;done=false;requested=true;});
                ctx.waitFor(mc->done,100);
                ctx.runOnClient(mc->{if(failure!=null)throw new AssertionError("Native world Snowstorm coordinate audit failed",failure);});
            }
            System.out.println("PORT_PLATFORM_SNOWSTORM_SPACE: actual BEFORE_ENTITIES emitter deferred vertices match native world-view centers after simulation mutation: "+String.join(" | ",receipts));
        } finally {ctx.runOnClient(mc->{requested=false;receipts.clear();mc.player.setYRot(original[0]);mc.player.setXRot(original[1]);});}
    }
    private record Vertices(List<Vector3f> points) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setColor(int color){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        public VertexConsumer setLineWidth(float width){return this;}
    }
    private PlatformSnowstormSpace(){}
}
