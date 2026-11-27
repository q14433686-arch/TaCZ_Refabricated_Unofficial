package com.tacz.guns.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.*;
import com.tacz.guns.api.item.IGun;
import java.util.*;

/** Cosmetic event tokens only: no remote scripts, coordinates or particle parameters. */
public final class AnimationParticleMessages {
    private AnimationParticleMessages(){}
    public record Request(Identifier gun,String token) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Identifier.parse("tacz:animation_particle_request"));
        public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->{b.writeIdentifier(p.gun);b.writeUtf(p.token,64);},b->new Request(b.readIdentifier(),b.readUtf(64)));
        public Request { if(!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid particle token"); }
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Delivery(int owner,Identifier gun,String token) implements CustomPacketPayload {
        public static final Type<Delivery> TYPE=new Type<>(Identifier.parse("tacz:animation_particle_delivery"));
        public static final StreamCodec<FriendlyByteBuf,Delivery> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.owner);b.writeIdentifier(p.gun);b.writeUtf(p.token,64);},
            b->new Delivery(b.readVarInt(),b.readIdentifier(),b.readUtf(64)));
        public Delivery { if(!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid particle token"); }
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private record Rate(long window,int count){}
    private static final Map<ServerPlayer,Rate> RATES=Collections.synchronizedMap(new WeakHashMap<>());
    public static void relay(Request request,ServerPlayer sender){
        var stack=sender.getMainHandItem();
        if(!sender.isAlive()||!(stack.getItem() instanceof IGun gun)||!gun.getGunId(stack).equals(request.gun))return;
        long tick=sender.level().getGameTime();var old=RATES.get(sender);
        if(old==null||tick<old.window||tick-old.window>=20)old=new Rate(tick,0);
        if(old.count>=64)return;RATES.put(sender,new Rate(old.window,old.count+1));
        var delivery=new Delivery(sender.getId(),request.gun,request.token);
        for(var target:PlayerLookup.tracking(sender))if(target!=sender&&target.distanceToSqr(sender)<=128*128
                &&ServerPlayNetworking.canSend(target,Delivery.TYPE))ServerPlayNetworking.send(target,delivery);
    }
}
