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
    // 目标是 loadResources(...) 里那个 thenCompose 的 lambda。
    //
    // 【不要写成 "lambda$loadResources$2"】: 那是 26.x 非混淆版本下 javac 生成的合成名。
    // 1.21.11 是混淆版本，Yarn/intermediary 给这个 lambda 分配了正式的中间名
    // method_58296，refmap 里【没有】 lambda$loadResources$N 的条目 —— 该名字会被原样
    // 传给 mixin，在混淆类里当然找不到，于是 @ModifyArg 报
    // "could not find any targets matching 'lambda$loadResources$2'" 并在 APPLY 阶段崩溃。
    //
    // 用 intermediary 名 + 完整描述符定位。javap 已确认 1.21.11 中：
    //   private static CompletionStage method_58296(FeatureFlagSet, Commands$CommandSelection,
    //       List, PermissionSet, ResourceManager, Executor, Executor,
    //       ReloadableServerRegistries$LoadResult)
    // 其中 slot 8 存放新建的 ReloadableServerResources（对应 @Local(ordinal = 0)），
    // 最后一个形参是 LoadResult（对应 @Local(argsOnly = true)），
    // 且它内部确实调用了 SimpleReloadInstance.create(...)，index=1 即 listeners 列表。
    @ModifyArg(
            method = "method_58296(Lnet/minecraft/world/flag/FeatureFlagSet;Lnet/minecraft/commands/Commands$CommandSelection;Ljava/util/List;Lnet/minecraft/server/permissions/PermissionSet;Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Lnet/minecraft/server/ReloadableServerRegistries$LoadResult;)Ljava/util/concurrent/CompletionStage;",
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
