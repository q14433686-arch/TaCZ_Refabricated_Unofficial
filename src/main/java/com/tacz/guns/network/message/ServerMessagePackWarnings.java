package com.tacz.guns.network.message;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Only display names, never paths, commands or executable server-provided text. */
public record ServerMessagePackWarnings(List<String> packs) implements CustomPacketPayload {
    public ServerMessagePackWarnings { packs = List.copyOf(packs.stream().limit(256).map(s -> s.substring(0,Math.min(s.length(),256))).toList()); }
    public static final Type<ServerMessagePackWarnings> TYPE = new Type<>(Identifier.parse("tacz:pack_warnings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ServerMessagePackWarnings> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeVarInt(p.packs.size());for(String name:p.packs)buf.writeUtf(name,256);},
        buf->{int size=buf.readVarInt();if(size<0||size>256)throw new IllegalArgumentException("Invalid warning list size");
            List<String> names=new ArrayList<>();for(int i=0;i<size;i++)names.add(buf.readUtf(256));return new ServerMessagePackWarnings(names);});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @Environment(EnvType.CLIENT)
    public static void handle(ServerMessagePackWarnings p,ClientPlayNetworking.Context ctx){
        ctx.client().execute(()->com.tacz.guns.client.event.PackCompatibilityNotice.accept(p.packs));
    }
}
