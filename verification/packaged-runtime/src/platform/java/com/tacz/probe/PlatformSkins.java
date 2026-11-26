package com.tacz.probe;

import com.tacz.guns.client.renderer.other.TargetOwnerSkins;
import com.tacz.guns.util.TargetOwnerProfiles;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Control only the network lookup future; run vanilla getOrDefault and the shipped renderer
 * helper unchanged. No live Mojang account/network availability is asserted by this fixture. */
public final class PlatformSkins {
    public static ResolvableProfile watched;
    public static CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> future;
    static void verify(ClientGameTestContext ctx) {
        try {
            var ready=ctx.computeOnClient(mc -> {
                if(TargetOwnerProfiles.fromName(null)!=null || TargetOwnerProfiles.fromName(" ")!=null)
                    throw new AssertionError("Blank target owner produced a profile");
                var target=TargetOwnerProfiles.fromName("port_target");
                var expected=DefaultPlayerSkin.get(target.partialProfile()).body().texturePath();
                PlayerSkinRenderCache.RenderInfo readyInfo=null;
                for(int i=0;i<32;i++) {
                    watched=TargetOwnerProfiles.fromName("port_ready_"+i);
                    future=CompletableFuture.completedFuture(Optional.empty());
                    var info=mc.playerSkinRenderCache().getOrDefault(watched);
                    if(!info.playerSkin().body().texturePath().equals(expected)) { readyInfo=info;break; }
                }
                if(readyInfo==null) throw new AssertionError("No distinct native skin for asynchronous switch control");
                watched=target;future=new CompletableFuture<>();
                if(!TargetOwnerSkins.texture(target).equals(expected) || future.isDone()) throw new AssertionError("Pending lookup did not return the default immediately");
                return readyInfo;
            });
            CompletableFuture.runAsync(() -> future.complete(Optional.of(ready))).join();
            ctx.waitFor(mc -> TargetOwnerSkins.texture(watched).equals(ready.playerSkin().body().texturePath()));
            ctx.runOnClient(mc -> {
                future=CompletableFuture.failedFuture(new IllegalStateException("controlled offline lookup"));
                var fallback=DefaultPlayerSkin.get(watched.partialProfile()).body().texturePath();
                if(!TargetOwnerSkins.texture(watched).equals(fallback)) throw new AssertionError("Failed async lookup did not fall back");
                future=CompletableFuture.completedFuture(Optional.empty());
                if(!TargetOwnerSkins.texture(watched).equals(fallback)) throw new AssertionError("Unknown owner did not fall back");
            });
            System.out.println("PORT_PLATFORM_SKIN: unresolved name/blank, nonblocking pending, async-ready native skin switch, empty and exceptional lookup fallback passed; controlled lookup only");
        } finally { ctx.runOnClient(mc -> { watched=null;future=null; }); }
    }
    private PlatformSkins() {}
}
