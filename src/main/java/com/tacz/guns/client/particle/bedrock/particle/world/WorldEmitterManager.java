package com.tacz.guns.client.particle.bedrock.particle.world;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.*;
import com.tacz.guns.client.particle.bedrock.particle.data.component.*;
import com.tacz.guns.client.particle.bedrock.particle.runtime.*;
import com.tacz.guns.client.particle.bedrock.particle.render.BillboardHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import java.util.*;

/** Fabric 26.3 lifecycle and immutable SubmitNode geometry adapter. No vanilla smoke substitution. */
public final class WorldEmitterManager {
    private static final WorldEmitterManager INSTANCE=new WorldEmitterManager();
    private static final int MAX_EMITTERS=128, MAX_TOTAL_PARTICLES=16384;
    private final List<Active> active=new ArrayList<>(), pending=new ArrayList<>();
    private ClientLevel level;
    private boolean registered;
    private int eventBudget;
    private long spawned, rendered;
    private record Active(ParticleEmitterInstance emitter, Vec3 velocity) {}
    public static WorldEmitterManager getInstance(){return INSTANCE;}
    public int emitterCount(){return active.size()+pending.size();}
    public int particleCount(){return active.stream().mapToInt(e->e.emitter.getParticleCount()).sum();}
    public long spawned(){return spawned;}
    public long rendered(){return rendered;}
    public void clear(){active.clear();pending.clear();eventBudget=0;level=null;}
    public void register(){
        if(registered)return;registered=true;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.level!=level){clear();level=mc.level;}
            if(mc.level!=null&&!mc.isPaused())tick();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx->{
            if(Minecraft.getInstance().level==level)submit(ctx.submitNodeCollector(),ctx.levelState().cameraRenderState.pos);
        });
    }
    public ParticleEmitterInstance addEmitter(ClientLevel world,Vec3 pos,Vec3 velocity,ParticleEffectDefinition definition){
        return addEmitter(world,new Matrix4f().translation((float)pos.x,(float)pos.y,(float)pos.z),velocity,definition,"");
    }
    public ParticleEmitterInstance addEmitter(ClientLevel world,Matrix4f transform,Vec3 velocity,ParticleEffectDefinition definition,String script){
        if(world==null||definition==null)return null;
        if(world!=level){clear();level=world;}
        if(emitterCount()>=MAX_EMITTERS||particleCount()>=MAX_TOTAL_PARTICLES||eventBudget++>=256)return null;
        var env=new ParticleMolangEnvironment();
        // Pre-effect variables must exist when initialization/lifetime components are applied.
        if(script!=null&&!script.isBlank())env.eval(script);
        var emitter=new ParticleEmitterInstance(definition,env);
        emitter.setEmitterTransform(transform,transform);
        var pos=transform.getTranslation(new Vector3f());
        emitter.setEventContext(new EventExecutor.EventContext(emitter,env,world,new Vec3(pos.x,pos.y,pos.z)));
        pending.add(new Active(emitter,velocity));spawned++;
        emitter.fireCreationEvents();
        return emitter;
    }
    public void tick(){
        eventBudget=0;
        active.addAll(pending);pending.clear();
        int remaining=MAX_TOTAL_PARTICLES;
        for(var a:List.copyOf(active)){
            var emitter=a.emitter;
            try{
                if(remaining<=0){emitter.discard();continue;}
                var transform=new Matrix4f(emitter.getWorldTransform()).translateLocal((float)a.velocity.x/20,(float)a.velocity.y/20,(float)a.velocity.z/20);
                emitter.setEmitterTransform(transform,transform);
                var pos=transform.getTranslation(new Vector3f());
                emitter.setEventContext(new EventExecutor.EventContext(emitter,emitter.getMolang(),level,new Vec3(pos.x,pos.y,pos.z)));
                emitter.tick(.05f);
                remaining-=emitter.getParticleCount();
            }catch(RuntimeException ex){emitter.discard();GunMod.LOGGER.error("Snowstorm emitter failed: {}",emitter.getDefinition().getIdentifier(),ex);}
        }
        active.removeIf(a->a.emitter.isFinished());
    }
    private void submit(SubmitNodeCollector collector,Vec3 camera){
        for(var a:active){
            var emitter=a.emitter;var definition=emitter.getDefinition();var desc=definition.getDescription();
            RenderType type=switch(desc.getMaterial()){
                case PARTICLES_OPAQUE -> RenderTypes.entityCutout(desc.getTexture());
                case PARTICLES_ADD,ENERGY_SWIRL -> RenderTypes.energySwirl(desc.getTexture(),0,0);
                case PARTICLES_BLEND,PARTICLES_ALPHA,PARTICLES_EMISSIVE -> RenderTypes.entityTranslucent(desc.getTexture());
            };
            var billboard=definition.findComponent(ParticleAppearanceBillboard.class);
            var mode=billboard==null?ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ:billboard.faceCameraMode();
            Matrix4f transform=new Matrix4f(emitter.getWorldTransform());
            boolean localRotation=emitter.isLocalRotation();
            boolean lit=definition.findComponent(ParticleAppearanceLighting.class)!=null;
            for(var particle:emitter.getParticles()){
                // Resolve mutable simulation state during extraction, never in a delayed callback.
                var p=snapshot(particle);
                if(!Float.isFinite(p.x+p.y+p.z+p.width+p.height+p.a)||!p.alive)continue;
                PoseStack pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);
                var wp=new Vector3f(p.x,p.y,p.z);if(!p.worldSpace)transform.transformPosition(wp);
                int light=lit?net.minecraft.client.renderer.LevelRenderer.getLightCoords(level,net.minecraft.core.BlockPos.containing(wp.x,wp.y,wp.z)):15728880;
                collector.submitCustomGeometry(pose,type,(entry,consumer)->{
                    PoseStack frozen=new PoseStack();frozen.last().pose().set(entry.pose());frozen.last().normal().set(entry.normal());
                    BillboardHelper.renderBillboard(p,frozen,consumer,light,mode,transform,!p.worldSpace,localRotation,0,0,null);
                });rendered++;
            }
        }
    }
    private static ParticleInstance snapshot(ParticleInstance p){
        var q=new ParticleInstance();q.x=p.x;q.y=p.y;q.z=p.z;q.vx=p.vx;q.vy=p.vy;q.vz=p.vz;
        q.width=p.width;q.height=p.height;q.r=p.r;q.g=p.g;q.b=p.b;q.a=p.a;q.rotation=p.rotation;
        q.u0=p.u0;q.v0=p.v0;q.u1=p.u1;q.v1=p.v1;q.spawnScale=p.spawnScale;q.worldSpace=p.worldSpace;q.fpDetached=p.fpDetached;q.alive=p.alive;return q;
    }
}
