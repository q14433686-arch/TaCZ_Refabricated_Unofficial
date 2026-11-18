package com.tacz.guns.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationParticleEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.*;
import org.joml.*;
import java.util.*;

/** Render-bound keyframes, independent from shot callbacks. No substitution for unknown effects. */
public final class AnimationParticleDispatcher {
    private static final Set<String> WARNED=new HashSet<>();
    private static long submitted;
    private AnimationParticleDispatcher(){}
    public static long submitted(){return submitted;}
    public static void clear(){WARNED.clear();}
    private static void warn(String key){if(WARNED.add(key))GunMod.LOGGER.warn("Animation particle not played: {}",key);}
    public static void submit(ItemStack stack,BedrockGunModel model,PoseStack root,ItemDisplayContext mode,SubmitNodeCollector collector,int light){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mode==null)return;
        boolean first=mode.firstPerson();
        if(!first&&(!GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT||NativeMuzzleEffects.currentOwner()!=mc.player.getId()))return;
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null||display.getAnimationStateMachine()==null)return;
        var events=display.getAnimationStateMachine().getAnimationController().consumeParticleEvents();
        for(var event:events){
            String locator=event.locator()==null||event.locator().isBlank()?com.tacz.guns.client.model.GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE:event.locator();
            var node=model.getNode(locator);
            if(node==null){warn(event.effect()+": missing locator "+locator);continue;}
            Deque<BedrockPart> chain=new ArrayDeque<>();
            for(var n=node;n!=null;n=n.getParent())chain.push(n);
            PoseStack pose=new PoseStack();pose.last().pose().set(root.last().pose());pose.last().normal().set(root.last().normal());
            while(!chain.isEmpty())chain.pop().translateAndRotateAndScale(pose);
            Matrix4f world=first?GunItemRendererWrapper.muzzleWorldPose(pose.last().pose())
                    :new Matrix4f(pose.last().pose()).translateLocal((float)mc.gameRenderer.mainCamera().position().x,
                        (float)mc.gameRenderer.mainCamera().position().y,(float)mc.gameRenderer.mainCamera().position().z);
            var definition=com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader.getInstance().getDefinition(event.effect());
            if(definition!=null){
                var emitter=com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager.getInstance()
                        .addEmitter(mc.level,world,net.minecraft.world.phys.Vec3.ZERO,definition,event.preEffectScript());
                if(emitter!=null)submitted++;
                continue;
            }
            if(event.preEffectScript()!=null&&!event.preEffectScript().isBlank()){
                warn(event.effect()+": scripts require a Snowstorm definition, not a parameterless native particle");continue;
            }
            if(NativeMuzzleEffects.submitAnimationProfile(event.effect(),pose,world,first,light,collector)){submitted++;continue;}
            var type=BuiltInRegistries.PARTICLE_TYPE.getValue(event.effect());
            if(type instanceof SimpleParticleType simple){
                Vector3f origin=world.getTranslation(new Vector3f());
                mc.particleEngine.createParticle(simple,origin.x,origin.y,origin.z,0,0,0);submitted++;
            }else warn(event.effect()+": no native or parameterless Minecraft particle backend");
        }
    }
}
