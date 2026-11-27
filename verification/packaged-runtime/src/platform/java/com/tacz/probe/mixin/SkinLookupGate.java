package com.tacz.probe.mixin;

import com.tacz.probe.PlatformSkins;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinRenderCache.class)
abstract class SkinLookupGate {
    @Inject(method="lookup",at=@At("HEAD"),cancellable=true)
    private void lookup(ResolvableProfile profile,CallbackInfoReturnable<CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>>> cir) {
        if(profile.equals(PlatformSkins.watched)) cir.setReturnValue(PlatformSkins.future);
    }
}
