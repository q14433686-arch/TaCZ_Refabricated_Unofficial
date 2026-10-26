package com.tacz.guns.block.entity;

import net.minecraft.world.item.component.ResolvableProfile;

/**
 * 临时编译探针（提交后立即删除）：确认 26.2 的 ResolvableProfile 与客户端皮肤缓存 API。
 * 每行独立，编译失败时日志会逐行列出哪些符号不存在；用 `var x = ...; x.zzz();`
 * 让编译器把真实返回类型打印出来。
 */
public class ApiProbe {
    static void probe(ResolvableProfile p, net.minecraft.client.Minecraft mc,
                      com.mojang.authlib.GameProfile gp) {
        var a1 = ResolvableProfile.createUnresolved("Notch");
        a1.zzz();
        var a2 = p.gameProfile();
        a2.zzz();
        var a3 = p.partialProfile();
        a3.zzz();
        var a4 = p.name();
        a4.zzz();
        var a5 = p.id();
        a5.zzz();
        var a6 = p.skinPatch();
        a6.zzz();
        var a7 = ResolvableProfile.CODEC;
        a7.zzz();
        var a8 = ResolvableProfile.STREAM_CODEC;
        a8.zzz();

        var c1 = mc.getPlayerSkinRenderCache();
        c1.zzz();
        var c2 = net.minecraft.client.resources.PlayerSkinRenderCache.class;
        var k1 = c1.getOrDefault(p);
        k1.zzz();
        var k2 = c1.createLookup(p);
        k2.zzz();
        var k3 = c1.getOrDefault(p).body();
        k3.zzz();
        var k4 = mc.getSkinManager().createLookup(gp, false);
        k4.zzz();
    }
}
