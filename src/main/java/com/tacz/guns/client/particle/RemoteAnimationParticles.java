package com.tacz.guns.client.particle;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationParticleEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.message.AnimationParticleMessages.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Per-entity queues, resolved exclusively against locally trusted gun animation definitions. */
public final class RemoteAnimationParticles {
    private record Pending(Identifier gun,long tick,AnimationParticleEvent event){}
    private static Object level;
    private static final Map<Integer,List<Pending>> PENDING=new HashMap<>();
    private RemoteAnimationParticles(){}
    public static void clear(){PENDING.clear();level=null;}
    private static void synchronize(){
        var mc=Minecraft.getInstance();if(mc.level!=level){clear();level=mc.level;}
        if(mc.level!=null){long now=mc.level.getGameTime();PENDING.values().forEach(q->q.removeIf(e->now<e.tick||now-e.tick>20));PENDING.values().removeIf(List::isEmpty);}
    }
    public static void send(Identifier gun,AnimationParticleEvent event){
        if(ClientPlayNetworking.canSend(Request.TYPE))ClientPlayNetworking.send(new Request(gun,event.token()));
    }
    public static void accept(Delivery message){
        synchronize();var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||message.owner()==mc.player.getId())return;
        if(!(mc.level.getEntity(message.owner()) instanceof LivingEntity owner)||!owner.isAlive()||owner.distanceToSqr(mc.player)>128*128)return;
        var stack=owner.getMainHandItem();if(!(stack.getItem() instanceof IGun gun)||!gun.getGunId(stack).equals(message.gun()))return;
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null||display.getAnimationStateMachine()==null)return;
        var event=display.getAnimationStateMachine().getAnimationController().resolveParticleToken(message.token());
        // A sender cannot introduce a script/effect absent from this recipient's installed pack.
        if(event==null||PENDING.size()>=128&&!PENDING.containsKey(message.owner()))return;
        var queue=PENDING.computeIfAbsent(message.owner(),key->new ArrayList<>());
        if(queue.size()<32)queue.add(new Pending(message.gun(),mc.level.getGameTime(),event));
    }
    public static List<AnimationParticleEvent> consume(int owner,Identifier gun){
        synchronize();var queue=PENDING.remove(owner);if(queue==null)return List.of();
        return queue.stream().filter(p->p.gun.equals(gun)).map(Pending::event).toList();
    }
}
