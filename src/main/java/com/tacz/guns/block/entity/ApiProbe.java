package com.tacz.guns.block.entity;

/** 临时探针 3（提交后立即删除）：用 `var` + 故意写错的方法名，让编译器把真实类型打印出来。 */
public class ApiProbe {
    static void probe(net.minecraft.server.MinecraftServer server) {
        var resolver = server.services().profileResolver();
        resolver.zzz();
        var byName = resolver.findProfileByName("Notch");
        byName.zzz();
        var cache = server.services().nameToIdCache();
        cache.zzz();
        var cached = cache.getAsync("Notch");
        cached.zzz();
        var repo = server.services().profileRepository();
        var repoByName = repo.findProfileByName("Notch");
        repoByName.zzz();
    }
}
