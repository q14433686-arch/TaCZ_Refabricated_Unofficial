package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * OpenGL stencil state used by the first-person scope render types.
 *
 * <p>Minecraft 26.1.2 records feature geometry first and only issues GPU draws when
 * {@code MultiBufferSource.BufferSource#endBatch()} is called. Consequently stencil calls made from a
 * {@code SubmitNodeCollector.CustomGeometryRenderer} only surround CPU vertex emission and cannot affect the
 * delayed draw. {@link ScopeStencilRenderType} marks the real draw, and the GlCommandEncoder mixin calls
 * {@link #prepareCurrentDraw()} immediately before {@code glDraw*}.</p>
 *
 * <p>A shared {@code GL_STENCIL_INDEX8} renderbuffer is attached to the currently bound FBO. It does not
 * replace Iris' or vanilla's depth attachment. The one renderbuffer is resized when the viewport changes and
 * may be attached to both Iris mask/body FBOs, preserving the mask even if Iris selects two framebuffer objects;
 * this avoids the per-draw leak and depth-buffer destruction of the old {@code GL_DEPTH24_STENCIL8}
 * workaround.</p>
 */
public final class ScopeStencilState {
    public enum Phase {
        NONE,
        WRITE_MASK,
        DRAW_OUTSIDE,
        DRAW_INSIDE
    }

    private static final ThreadLocal<Phase> CURRENT_PHASE = ThreadLocal.withInitial(() -> Phase.NONE);

    private static int sharedStencilRenderbuffer;
    private static int sharedStencilWidth;
    private static int sharedStencilHeight;
    private static boolean currentDrawPrepared;
    private static boolean currentDrawAllowed = true;
    private static boolean loggedActive;
    private static boolean loggedUnavailable;

    private ScopeStencilState() {
    }

    public static void begin(Phase phase) {
        RenderSystem.assertOnRenderThread();
        CURRENT_PHASE.set(phase);
        currentDrawPrepared = false;
        currentDrawAllowed = true;
    }

    /**
     * Called by the command-encoder mixin after the destination framebuffer and shader are bound.
     *
     * @return whether the pending {@code drawFromBuffers} should execute. If stencil is unavailable, the
     * mask writer and inside-only reticle are skipped, while the outside/body phase is allowed as an ordinary
     * draw to provide the transparent-ocular fallback.
     */
    public static boolean prepareCurrentDraw() {
        RenderSystem.assertOnRenderThread();
        Phase phase = CURRENT_PHASE.get();
        if (phase == Phase.NONE) {
            return true;
        }
        if (currentDrawPrepared) {
            return currentDrawAllowed;
        }
        currentDrawPrepared = true;

        if (!ensureStencilAttachment()) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            currentDrawAllowed = phase == Phase.DRAW_OUTSIDE;
            return currentDrawAllowed;
        }

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP,
                phase == Phase.WRITE_MASK ? GL11.GL_REPLACE : GL11.GL_KEEP);

        switch (phase) {
            case WRITE_MASK -> {
                GL11.glStencilMask(0xFF);
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            }
            case DRAW_OUTSIDE -> {
                GL11.glStencilMask(0x00);
                GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF);
            }
            case DRAW_INSIDE -> {
                GL11.glStencilMask(0x00);
                GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            }
            default -> GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        currentDrawAllowed = true;
        return true;
    }

    public static void end() {
        RenderSystem.assertOnRenderThread();
        GL11.glStencilMask(0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        currentDrawPrepared = false;
        currentDrawAllowed = true;
        CURRENT_PHASE.set(Phase.NONE);
    }

    private static boolean ensureStencilAttachment() {
        int fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (fbo == 0) {
            // The default framebuffer cannot receive an attachment. It may still provide stencil itself.
            return GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0;
        }

        int objectType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (objectType != GL11.GL_NONE) {
            return true;
        }

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int width = Math.max(1, viewport[2]);
        int height = Math.max(1, viewport[3]);

        if (sharedStencilRenderbuffer == 0 || !GL30.glIsRenderbuffer(sharedStencilRenderbuffer)) {
            sharedStencilRenderbuffer = GL30.glGenRenderbuffers();
            sharedStencilWidth = 0;
            sharedStencilHeight = 0;
        }

        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, sharedStencilRenderbuffer);
        if (sharedStencilWidth != width || sharedStencilHeight != height) {
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, width, height);
            sharedStencilWidth = width;
            sharedStencilHeight = height;
        }
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                sharedStencilRenderbuffer
        );
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    0
            );
            logUnavailable("framebuffer " + fbo + " is incomplete after adding stencil (status=0x"
                    + Integer.toHexString(status) + ")");
            return false;
        }
        if (!loggedActive) {
            loggedActive = true;
            GunMod.LOGGER.info("[TACZ Scope] Draw-time stencil clipping active (fbo={}, size={}x{}).",
                    fbo, width, height);
        }
        return true;
    }

    private static void logUnavailable(String reason) {
        if (!loggedUnavailable) {
            loggedUnavailable = true;
            GunMod.LOGGER.warn("[TACZ Scope] Stencil clipping unavailable: {}. Using the transparent-ocular fallback.", reason);
        }
    }

}
