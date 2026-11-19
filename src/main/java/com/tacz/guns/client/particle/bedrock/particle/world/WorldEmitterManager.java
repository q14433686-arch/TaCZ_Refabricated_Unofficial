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
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
    private static final class Active {
        final ParticleEmitterInstance emitter;final Vec3 velocity;
        int owner=-1;String locator;net.minecraft.resources.Identifier gun;boolean first;
        Active(ParticleEmitterInstance emitter,Vec3 velocity){this.emitter=emitter;this.velocity=velocity;}
    }
    public void bindAnimated(ParticleEmitterInstance emitter,int owner,net.minecraft.resources.Identifier gun,String locator,boolean first,Matrix4f hand,Matrix4f world){
        for(var a:pending)if(a.emitter==emitter){
            a.owner=owner;a.gun=gun;a.locator=locator;a.first=first;
            emitter.getMolang().bindEntity(level.getEntity(owner));
            if(first){var fp=emitter.getDefinition().findComponent(FPEmitterLocalSpace.class);
                if(fp==null)emitter.enableFPMode(true,true,true,false);
                else emitter.enableFPMode(fp.position(),fp.rotation(),fp.velocity(),fp.toWorld());
                emitter.setEmitterTransform(hand,world);
            }return;
        }
    }
    public void updateAnchors(int owner,net.minecraft.resources.Identifier gun,com.tacz.guns.client.model.BedrockGunModel model,
                              PoseStack root,boolean first,SubmitNodeCollector collector,int light){
        for(var a:all())if(a.owner==owner&&gun.equals(a.gun)){
            var node=model.getNode(a.locator);if(node==null)continue;
            var chain=new java.util.ArrayDeque<com.tacz.guns.client.model.bedrock.BedrockPart>();
            for(var n=node;n!=null;n=n.getParent())chain.push(n);
            PoseStack pose=new PoseStack();pose.last().pose().set(root.last().pose());pose.last().normal().set(root.last().normal());
            while(!chain.isEmpty())chain.pop().translateAndRotateAndScale(pose);
            Matrix4f world=first?com.tacz.guns.client.renderer.item.GunItemRendererWrapper.muzzleWorldPose(pose.last().pose())
                :new Matrix4f(pose.last().pose()).translateLocal((float)Minecraft.getInstance().gameRenderer.mainCamera().position().x,
                    (float)Minecraft.getInstance().gameRenderer.mainCamera().position().y,(float)Minecraft.getInstance().gameRenderer.mainCamera().position().z);
            a.emitter.setEmitterTransform(first?new Matrix4f(pose.last().pose()):world,world);
            if(first)submitEmitter(collector,a,Vec3.ZERO,true,light);
        }
    }
    private List<Active> all(){var result=new ArrayList<>(active);result.addAll(pending);return result;}
    public static WorldEmitterManager getInstance(){return INSTANCE;}
    public int emitterCount(){return active.size()+pending.size();}
    public int particleCount(){return active.stream().mapToInt(e->e.emitter.getParticleCount()).sum();}
    public long spawned(){return spawned;}
    public long rendered(){return rendered;}
    public void clear(){SnowstormMaterials.clearTextures();active.clear();pending.clear();eventBudget=0;level=null;com.tacz.guns.client.particle.RemoteAnimationParticles.clear();}
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
        return addEmitter(world,transform,velocity,definition,script,null);
    }
    public ParticleEmitterInstance addEmitter(ClientLevel world,Matrix4f transform,Vec3 velocity,ParticleEffectDefinition definition,String script,net.minecraft.world.entity.Entity owner){
        if(world==null||definition==null)return null;
        if(world!=level){clear();level=world;}
        if(emitterCount()>=MAX_EMITTERS||particleCount()>=MAX_TOTAL_PARTICLES||eventBudget++>=256)return null;
        var env=new ParticleMolangEnvironment();
        env.bindEntity(owner);
        // Pre-effect variables must exist when initialization/lifetime components are applied.
        final ParticleEmitterInstance emitter;
        try {
            if(script!=null&&!script.isBlank())env.eval(script);
            emitter=new ParticleEmitterInstance(definition,env);
        } catch(RuntimeException ex) {
            GunMod.LOGGER.error("Cannot initialize Snowstorm effect {}",definition.getIdentifier(),ex);return null;
        }
        emitter.disableFPMode();
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
        int capacity=Math.max(0,MAX_TOTAL_PARTICLES-particleCount());
        for(var a:List.copyOf(active)){
            var emitter=a.emitter;
            if(a.owner>=0){
                var entity=level.getEntity(a.owner);
                if(!(entity instanceof net.minecraft.world.entity.LivingEntity living)||!entity.isAlive()
                        ||!(living.getMainHandItem().getItem() instanceof com.tacz.guns.api.item.IGun gun)||!a.gun.equals(gun.getGunId(living.getMainHandItem()))) {
                    emitter.setRemoved(true);emitter.setActive(false);
                }
            }
            try{
                int before=emitter.getParticleCount();
                emitter.setParticleBudget(before+capacity);
                var transform=new Matrix4f(emitter.getWorldTransform()).translateLocal((float)a.velocity.x/20,(float)a.velocity.y/20,(float)a.velocity.z/20);
                emitter.setEmitterTransform(a.first?emitter.getEmitterTransform():transform,transform);
                var pos=transform.getTranslation(new Vector3f());
                emitter.setEventContext(new EventExecutor.EventContext(emitter,emitter.getMolang(),level,new Vec3(pos.x,pos.y,pos.z)));
                emitter.tick(.05f);
                capacity+=before-emitter.getParticleCount();
            }catch(RuntimeException ex){emitter.discard();GunMod.LOGGER.error("Snowstorm emitter failed: {}",emitter.getDefinition().getIdentifier(),ex);}
        }
        active.removeIf(a->a.emitter.isFinished());
    }
    private void submit(SubmitNodeCollector collector,Vec3 camera){
        for(var a:active)submitEmitter(collector,a,camera,false,15728880);
    }
    private void submitEmitter(SubmitNodeCollector collector,Active a,Vec3 camera,boolean hand,int inheritedLight){
            var emitter=a.emitter;var definition=emitter.getDefinition();var desc=definition.getDescription();
            boolean clipped=a.first && com.tacz.guns.client.render.scope.ScopeParticleClipping.ready(hand);
            RenderType type=SnowstormMaterials.type(desc,clipped,hand);
            // Rotate the actual camera's world Y into view space; includes pitch and roll in all passes.
            var viewRotation = new Matrix4f().rotation(new org.joml.Quaternionf(Minecraft.getInstance().gameRenderer.mainCamera().rotation()).conjugate());
            var billboard=definition.findComponent(ParticleAppearanceBillboard.class);
            var mode=billboard==null?ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ:billboard.faceCameraMode();
            Matrix4f transform=new Matrix4f(hand?emitter.getEmitterTransform():emitter.getWorldTransform());
            boolean localRotation=emitter.isLocalRotation();
            boolean lit=definition.findComponent(ParticleAppearanceLighting.class)!=null;
            for(var particle:emitter.getParticles()){
                if(hand ? particle.worldSpace : (!particle.worldSpace && a.first))continue;
                var init=definition.findComponent(ParticleInitialization.class);
                if(init!=null&&init.perRenderExpression()!=null){
                    emitter.bindContextAndCurves();
                    emitter.getMolang().bindParticle(particle.age,particle.maxLifetime,particle.random1,particle.random2,particle.random3,particle.random4);
                    emitter.bindCurves();
                    init.perRenderExpression().evaluate(emitter.getMolang().getContext());
                }
                // Resolve mutable simulation state during extraction, never in a delayed callback.
                var p=snapshot(particle);
                if(!Float.isFinite(p.x+p.y+p.z+p.width+p.height+p.a)||!p.alive)continue;
                PoseStack pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);
                var wp=new Vector3f(p.x,p.y,p.z);if(!p.worldSpace)transform.transformPosition(wp);
                var blockPos=net.minecraft.core.BlockPos.containing(wp.x,wp.y,wp.z);
                int light=hand?inheritedLight:lit?net.minecraft.util.LightCoordsUtil.pack(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,blockPos),
                        level.getBrightness(net.minecraft.world.level.LightLayer.SKY,blockPos)):15728880;
                collector.submitCustomGeometry(pose,type,(entry,consumer)->{
                    PoseStack frozen=new PoseStack();frozen.last().pose().set(entry.pose());frozen.last().normal().set(entry.normal());
                    BillboardHelper.renderBillboard(p,frozen,consumer,light,mode,transform,!p.worldSpace,localRotation,viewRotation);
                });rendered++;
            }
    }
    private static ParticleInstance snapshot(ParticleInstance p){
        var q=new ParticleInstance();q.x=p.x;q.y=p.y;q.z=p.z;q.vx=p.vx;q.vy=p.vy;q.vz=p.vz;
        q.width=p.width;q.height=p.height;q.r=p.r;q.g=p.g;q.b=p.b;q.a=p.a;q.rotation=p.rotation;
        q.u0=p.u0;q.v0=p.v0;q.u1=p.u1;q.v1=p.v1;q.spawnScale=p.spawnScale;q.worldSpace=p.worldSpace;q.fpDetached=p.fpDetached;q.alive=p.alive;return q;
    }
}
