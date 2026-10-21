package cn.sh1rocu.tacz.mixin.compat.jei;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional JEI 1.21.11 hook; MixinPlugin gates compat.jei on the jei mod id. */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.startup.ClientLifecycleHandler", remap = false)
public abstract class JeiClientLifecycleHandlerMixin {
    // Name-only selector: JEI method is not overloaded; its Minecraft return type is
    // intermediary-mapped in production on 1.21.11, unlike the 26.3 named runtime.
    @Inject(method = "getReloadListener",
            at = @At("RETURN"), require = 0, remap = false)
    private void tacz$captureReloadListener(CallbackInfoReturnable<ResourceManagerReloadListener> cir) {
        RecipeViewerReloadBridge.setJeiReloadListener(cir.getReturnValue());
    }
}
