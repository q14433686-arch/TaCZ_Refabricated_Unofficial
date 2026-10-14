package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureHandle;
import net.minecraft.resources.Identifier;

/** Non-owning alias refreshed after drawing the mask and before drawing the scope. */
@Environment(EnvType.CLIENT)
public final class ScopeMaskTextureHandle implements TextureHandle {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_mask");
    private static final ScopeMaskTextureHandle INSTANCE = new ScopeMaskTextureHandle();
    private GpuTextureView view;

    private ScopeMaskTextureHandle() {}

    public static boolean syncToMaskTarget() {
        var target = ScopeMaskTarget.current();
        if (target == null || !ScopeMaskTarget.isAvailable()) {
            return false;
        }
        try {
            INSTANCE.view = target.getColorTextureView();
            BorrowedScopeTextures.put(ID, INSTANCE);
            return true;
        } catch (Exception e) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to expose mask texture; scope body clipping disabled.", e);
            return false;
        }
    }

    @Override
    public GpuTextureView textureView() {
        return view;
    }

    @Override
    public GpuSampler sampler() {
        // Binary mask: nearest sampling avoids ambiguous values at the aperture edge.
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
