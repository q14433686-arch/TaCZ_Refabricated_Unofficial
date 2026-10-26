package com.tacz.guns.block.entity;

/** 临时探针 4（提交后立即删除）：UserNameToIdResolver / NameAndId 的方法名与返回类型。 */
public class ApiProbe {
    static void probe(net.minecraft.server.MinecraftServer server) {
        var cache = server.services().nameToIdCache();
        var a1 = cache.get("Notch");
        a1.zzz();
        var a2 = cache.resolve("Notch");
        a2.zzz();
        var a3 = cache.find("Notch");
        a3.zzz();
        var a4 = cache.lookup("Notch");
        a4.zzz();
        var nameAndId = server.services().profileRepository().findProfileByName("Notch").orElse(null);
        nameAndId.zzz();
        var fetched = server.services().sessionService().fetchProfile(java.util.UUID.randomUUID(), true);
        fetched.zzz();
    }
}
