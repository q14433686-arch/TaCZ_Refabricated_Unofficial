package com.tacz.guns.block.entity;

/**
 * 临时探针（提交后立即删除）：用编译器的报错来确认 26.2 里「按名字查档案」的 API 到底叫什么。
 * 每条候选独立成行，编译失败时日志会逐行列出哪些符号不存在。
 */
public class ApiProbe {
    static void probe(net.minecraft.server.MinecraftServer server, com.mojang.authlib.GameProfile profile) {
        Object c1 = server.getProfileCache();
        Object c2 = server.profileCache();
        Object c3 = server.services();
        Object c4 = server.getSessionService();
        Object c5 = server.sessionService();
        Object c6 = server.getPlayerList().getProfileCache();
        Class<?> c7 = net.minecraft.server.players.GameProfileCache.class;
        Class<?> c8 = net.minecraft.server.Services.class;
        Object c9 = profile.name();
        Object c10 = profile.id();
        Object c11 = profile.properties();
        Object c12 = profile.getProperties();
        Object c13 = net.minecraft.core.UUIDUtil.getOrCreatePlayerUUID(profile);
    }
}
