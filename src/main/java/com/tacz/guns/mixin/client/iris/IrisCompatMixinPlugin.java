package com.tacz.guns.mixin.client.iris;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies TACZ Iris-internal mixins only when Iris is actually loaded. */
public final class IrisCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("tacz");
    private static boolean loggedDecision;

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[TACZ Scope] Iris compat mixin config loaded: package={}, irisLoaded={}",
                mixinPackage, FabricLoader.getInstance().isModLoaded("iris"));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean apply = FabricLoader.getInstance().isModLoaded("iris");
        if (!loggedDecision) {
            loggedDecision = true;
            LOGGER.info("[TACZ Scope] Iris compat mixin decision: apply={}, firstMixin={}, firstTarget={}",
                    apply, mixinClassName, targetClassName);
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
