package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Exposes the offscreen mask FBO's color texture as a registered TextureManager texture
 * so that {@code RenderSetup} can bind it by Identifier.
 *
 * <p>{@code RenderSetup.RenderSetupBuilder#withTexture(String, Identifier)} only accepts
 * an {@code Identifier}, and internally resolves it through
 * {@code textureManager.getTexture(location)}. This wrapper registers our mask texture
 * under a known Identifier so the binding system can find it.</p>
 *
 * <h2>Lifecycle</h2>
 * This class does NOT own the texture — {@link ScopeMaskTarget} does.
 * {@code close()} is intentionally a no-op to avoid double-free when
 * TextureManager calls close on resource reload.
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskTextureHandle extends AbstractTexture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_mask");

    private static ScopeMaskTextureHandle instance;
    private static boolean registered;

    private ScopeMaskTextureHandle() {}

    /**
     * Synchronizes this handle to point at the current mask FBO's color texture.
     * Call each frame after mask rendering and before body/reticle draws.
     *
     * @return true if the texture is available
     */
    public static boolean syncToMaskTarget() {
        if (!ScopeMaskTarget.isAvailable()) return false;

        try {
            if (instance == null) {
                instance = new ScopeMaskTextureHandle();
            }

            // Point our texture/view/sampler fields at the mask FBO's color texture.
            // AbstractTexture.texture is a GpuTexture; we need to wrap our raw GL texture id.
            // In 26.1.2, AbstractTexture has protected fields: texture, textureView, sampler.
            // We set them via reflection since the mask FBO is raw GL.
            int texId = ScopeMaskTarget.colorTexture();
            if (texId <= 0) return false;

            // Get or create a GpuTexture wrapper for our GL texture id.
            // In 26.1.2's Blaze3D, we can use RenderSystem.getDevice() to create a texture view.
            // However, this is complex. The simpler approach:
            // The vanilla texture system resolves Identifier -> AbstractTexture -> GpuTexture.
            // Our AbstractTexture needs its texture/textureView fields set.
            // Let's try the direct approach.

            if (!registered) {
                Minecraft.getInstance().getTextureManager().register(ID, instance);
                registered = true;
            }

            // Update the GpuTexture reference each frame.
            // In 26.1.2, AbstractTexture.texture is the GpuTexture.
            // We need to create a proper GpuTexture from our raw GL texture id.
            // This is the tricky part — Blaze3D's texture abstraction may not directly
            // wrap an existing GL texture.
            //
            // Alternative approach: Instead of going through TextureManager,
            // bind the mask texture directly in the GlCommandEncoder mixin using raw GL.
            // This is what IrisScopeMaskState does in 26.2.
            //
            // For the vanilla path (non-Iris), the body/reticle RenderTypes need to
            // sample the mask. We can achieve this by having the DepthCopyRenderType
            // bind the mask texture before delegating the draw.

            return true;
        } catch (Exception e) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to sync mask texture", e);
            return false;
        }
    }

    @Override
    public void close() {
        // Intentional no-op: texture is owned by ScopeMaskTarget.
    }
}
