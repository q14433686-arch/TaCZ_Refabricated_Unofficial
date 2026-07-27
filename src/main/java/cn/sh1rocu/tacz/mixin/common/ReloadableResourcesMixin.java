package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.util.forge.EventHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableResourcesMixin {
    @ModifyArg(
            method = "lambda$loadResources$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"),
            index = 1
    )
    private static List<PreparableReloadListener> tacz$addReloadListener(List<PreparableReloadListener> original,
                                                                         @Local(ordinal = 0) ReloadableServerResources serverResources,
                                                                         @Local(argsOnly = true) ReloadableServerRegistries.LoadResult fullRegistries) {
        ArrayList<PreparableReloadListener> listeners = new ArrayList<>(original);
        listeners.addAll(EventHooks.onResourceReload(serverResources, fullRegistries.layers().compositeAccess()));
        return listeners;
    }
}
