package com.tacz.guns.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.item.component.ResolvableProfile;

/** Shared non-blocking vanilla skin lookup for named target heads. */
@Environment(EnvType.CLIENT)
public final class TargetSkinResolver {
    private TargetSkinResolver() {
    }

    /**
     * Starts or reuses vanilla's asynchronous profile/skin lookup and immediately returns the
     * current render info. While lookup is pending, and if it fails, vanilla supplies the default
     * skin through {@link PlayerSkinRenderCache#getOrDefault(ResolvableProfile)}.
     */
    public static PlayerSkinRenderCache.RenderInfo lookup(ResolvableProfile profile) {
        PlayerSkinRenderCache cache = Minecraft.getInstance().playerSkinRenderCache();
        try {
            return cache.createLookup(profile).get();
        } catch (RuntimeException exception) {
            return cache.getOrDefault(profile);
        }
    }
}
