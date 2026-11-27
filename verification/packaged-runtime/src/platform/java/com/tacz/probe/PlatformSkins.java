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
                // Use the shipped minecart accessor and render extraction, not just the profile factory.
                var cart=new com.tacz.guns.entity.TargetMinecart(com.tacz.guns.entity.TargetMinecart.TYPE,mc.level);
                var renderer=(com.tacz.guns.client.renderer.entity.TargetMinecartRenderer)mc.getEntityRenderDispatcher().getRenderer(cart);
                var state=(com.tacz.guns.client.renderer.entity.TargetMinecartRenderer.TargetMinecartRenderState)renderer.createRenderState();
                try {
                    if(cart.getOwnerProfile()!=null)throw new AssertionError("Unnamed cart produced an owner");
                    cart.setCustomName(net.minecraft.network.chat.Component.literal("port_target"));
                    var first=cart.getOwnerProfile();
                    if(first==null || first!=cart.getOwnerProfile() || !first.name().orElseThrow().equals("port_target"))
                        throw new AssertionError("Cart name did not produce a stable unresolved native profile");
                    renderer.extractRenderState(cart,state,0);
                    if(state.ownerProfile!=first)throw new AssertionError("Cart renderer lost the named owner");
                    cart.setCustomName(net.minecraft.network.chat.Component.literal("port_renamed"));
                    var renamed=cart.getOwnerProfile();
                    if(renamed==null || renamed==first || !renamed.name().orElseThrow().equals("port_renamed"))
                        throw new AssertionError("Cart rename retained a stale profile");
                    var picked=cart.getPickResult().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                    if(picked==null || !picked.getString().equals("port_renamed"))throw new AssertionError("Cart pick lost the renamed item");
                    renderer.extractRenderState(cart,state,0);
                    if(state.ownerProfile!=renamed)throw new AssertionError("Cart renderer retained pre-rename owner");
                    cart.setCustomName(null);renderer.extractRenderState(cart,state,0);
                    if(cart.getOwnerProfile()!=null || state.ownerProfile!=null)throw new AssertionError("Cleared cart retained an avatar");
                    cart.setCustomName(net.minecraft.network.chat.Component.literal(" "));
                    if(cart.getOwnerProfile()!=null)throw new AssertionError("Blank cart name produced a profile");
                } finally {cart.discard();}
                var target=TargetOwnerProfiles.fromName("port_target");
                var expected=DefaultPlayerSkin.get(target.partialProfile()).body().texturePath();
                PlayerSkinRenderCache.RenderInfo readyInfo=null;
                for(int i=0;i<32;i++) {
                    // Name-only partial profiles can share the NIL UUID and default skin.
                    // Distinct known UUIDs select real native fallback skins without network.
                    watched=ResolvableProfile.createUnresolved(new java.util.UUID(0,i+1));
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
            System.out.println("PORT_PLATFORM_SKIN: actual cart name/rename/clear/blank, stable native profile and renderer extraction/pick; nonblocking pending, async-ready native skin switch, empty and exceptional lookup fallback passed; controlled lookup only");
        } finally { ctx.runOnClient(mc -> { watched=null;future=null; }); }
    }
    private PlatformSkins() {}
}
