package com.tacz.guns.block.entity;

/** 临时探针 5（提交后立即删除）：NameAndId 的访问器 + 后台执行器。 */
public class ApiProbe {
    static void probe(net.minecraft.server.MinecraftServer server) {
        var nameAndId = server.services().nameToIdCache().get("Notch").orElse(null);
        var x1 = nameAndId.id();
        x1.zzz();
        var x2 = nameAndId.name();
        x2.zzz();
        var ex1 = net.minecraft.Util.backgroundExecutor();
        ex1.zzz();
        var ex2 = net.minecraft.util.Util.backgroundExecutor();
        ex2.zzz();
    }
}
