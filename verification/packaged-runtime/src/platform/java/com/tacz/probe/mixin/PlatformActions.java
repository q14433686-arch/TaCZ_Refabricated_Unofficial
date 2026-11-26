package com.tacz.probe.mixin;

import com.tacz.probe.PlatformSmoke;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the real operator entry, without canceling actions or replacing any result. */
@Mixin(LocalPlayer.class)
abstract class PlatformActions {
    @Inject(method="inspect",at=@At("HEAD"),remap=false)
    private void inspect(CallbackInfo ci) { PlatformSmoke.inspects++; }
    @Inject(method="reload",at=@At("HEAD"),remap=false)
    private void reload(CallbackInfo ci) { PlatformSmoke.reloads++; }
    @Inject(method="unloadAmmo",at=@At("HEAD"),remap=false)
    private void unload(CallbackInfo ci) { PlatformSmoke.unloads++; }
}
