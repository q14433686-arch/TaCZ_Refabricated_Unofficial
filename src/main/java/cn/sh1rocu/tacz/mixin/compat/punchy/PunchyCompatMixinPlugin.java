package cn.sh1rocu.tacz.mixin.compat.punchy;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Punchy first-person yield mixins only when Punchy is on the loading list.
 *
 * <p>On Fabric the loader entrypoint runs before mixin application, so unlike the
 * NeoForge sister repo (where {@code ModList} is not initialized yet when mixin plugins
 * run, forcing {@code FMLLoader#getLoadingModList}) {@link FabricLoader#isModLoaded}
 * is safe to query from a mixin config plugin. Punchy publishes as mod id
 * {@code punchy} (see docs/FIRST_PERSON_ANIMATION_COMPAT_26_2.md).</p>
 */
public final class PunchyCompatMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isModLoaded("punchy");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
