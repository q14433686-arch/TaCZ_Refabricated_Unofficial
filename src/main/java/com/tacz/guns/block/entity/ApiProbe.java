package com.tacz.guns.block.entity;

import java.util.UUID;

/** 临时探针 2（提交后立即删除）：Services 一族的方法名。 */
public class ApiProbe {
    static void probe(net.minecraft.server.MinecraftServer server, com.mojang.authlib.GameProfile profile) {
        Object d1 = server.services().sessionService();
        Object d2 = server.services().profileRepository();
        Object d3 = server.services().profileCache();
        Object d4 = server.services().nameToIdCache();
        Object d5 = server.services().profileResolver();
        Object d6 = server.services().sessionService().fetchProfile(UUID.randomUUID(), "Notch", true);
        Object d7 = server.services().profileRepository().findProfileByName("Notch");
        Object d8 = server.services().profileRepository().findProfileByUUID(UUID.randomUUID());
        Object d9 = server.services().profileCache().getAsync("Notch");
        Object d10 = net.minecraft.server.players.ProfileResolver.class;
        Object d11 = net.minecraft.server.Services.class.getRecordComponents();
    }
}
