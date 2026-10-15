package cn.sh1rocu.tacz.mixin.compat.jei;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures JEI's safe plugin-rebuild entry point without firing a recipe-update network event.
 * Gated by MixinPlugin's compat.jei package rule, so JEI remains optional.
 */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.startup.ClientLifecycleHandler", remap = false)
public abstract class JeiClientLifecycleHandlerMixin {
    @Inject(method = "getReloadListener()Lnet/minecraft/server/packs/resources/ResourceManagerReloadListener;",
            at = @At("RETURN"), require = 0, remap = false)
    private void tacz$captureReloadListener(CallbackInfoReturnable<ResourceManagerReloadListener> cir) {
        // The handler is client-wide and the returned listener already performs JEI's running and
        // client-thread checks. Do not retain a connection's RecipeMap or re-send stale recipes.
        RecipeViewerReloadBridge.setJeiReloadListener(cir.getReturnValue());
    }
}
