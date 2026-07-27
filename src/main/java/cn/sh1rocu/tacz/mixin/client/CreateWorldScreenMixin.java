package cn.sh1rocu.tacz.mixin.client;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import cn.sh1rocu.tacz.api.mixin.PackRepositoryExtension;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContextMapper;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.server.WorldLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    @Inject(
            method = "openCreateWorldScreen(Lnet/minecraft/client/Minecraft;Ljava/lang/Runnable;Ljava/util/function/Function;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContextMapper;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/gui/screens/worldselection/CreateWorldCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/WorldDataConfiguration;)Lnet/minecraft/server/WorldLoader$InitConfig;")
    )
    private static void tacz$addPacks(Minecraft minecraft, Runnable onClose,
                                      Function<WorldLoader.DataLoadContext, WorldGenSettings> worldGenSettings,
                                      WorldCreationContextMapper worldCreationContext,
                                      ResourceKey<WorldPreset> worldPreset,
                                      CreateWorldCallback createWorld,
                                      CallbackInfo ci,
                                      @Local(ordinal = 0) PackRepository repository) {
        AddPackFindersEvent event = new AddPackFindersEvent(PackType.SERVER_DATA, ((PackRepositoryExtension) repository)::tacz$addPackFinder, false);
        AddPackFindersEvent.CALLBACK.invoker().onAddPackFinders(event);
    }
}