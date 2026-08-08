package com.tacz.guns.client.render.scope;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * Offscreen RGBA8 mask target for the ocular scope clipping.
 *
 * <p>Replaces the old depth-buffer manipulation approach ({@code ScopeDepthCopyState}
 * BACKUP/RESTORE/APERTURE_COPY). The mask is a simple binary image:
 * white = ocular covers this pixel, black = ocular does not.
 * The scope body and reticle shaders sample it to decide per-pixel discard.</p>
 *
 * <p>Because the main depth buffer is never touched, downstream effects (fog, SSAO, SSR)
 * that read depth always see the correct hand depth — no "ghost hand" artifacts.</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskTarget {

    private static int fbo;
    private static int colorTexture;
    private static int width;
    private static int height;
    private static boolean failed;

    private ScopeMaskTarget() {
    }

    /**
     * Ensures the mask FBO exists and matches the current window size.
     *
     * @return true if the FBO is ready for use
     */
    public static boolean ensureReady() {
        if (failed) return false;

        Minecraft mc = Minecraft.getInstance();
        int w = Math.max(1, mc.getWindow().getWidth());
        int h = Math.max(1, mc.getWindow().getHeight());

        if (fbo > 0 && w == width && h == height) return true;

        // Window resized or first creation — destroy old resources
        destroy();

        try {
            // Create color texture (RGBA8)
            colorTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // Create FBO with only color attachment (no depth)
            fbo = GL30.glGenFramebuffers();
            int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, colorTexture, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL11.GL_NONE);

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                GunMod.LOGGER.error("[TACZ Scope] Mask FBO incomplete: 0x{}", Integer.toHexString(status));
                destroy();
                failed = true;
                return false;
            }

            width = w;
            height = h;
            GunMod.LOGGER.info("[TACZ Scope] Mask FBO created: {}x{}, fbo={}, tex={}", w, h, fbo, colorTexture);
            return true;
        } catch (Exception e) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to create mask FBO", e);
            destroy();
            failed = true;
            return false;
        }
    }

    public static int fbo() { return fbo; }
    public static int colorTexture() { return colorTexture; }
    public static int width() { return width; }
    public static int height() { return height; }
    public static boolean isAvailable() { return !failed && fbo > 0; }

    private static void destroy() {
        if (colorTexture > 0) {
            GL11.glDeleteTextures(colorTexture);
            colorTexture = 0;
        }
        if (fbo > 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        width = 0;
        height = 0;
    }
}
