package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * OpenGL stencil state used by the first-person scope render types.
 *
 * <p>Minecraft 26.1.2 records feature geometry first and only issues GPU draws when
 * {@code MultiBufferSource.BufferSource#endBatch()} is called. Consequently stencil calls made from a
 * {@code SubmitNodeCollector.CustomGeometryRenderer} only surround CPU vertex emission and cannot affect the
 * delayed draw. {@link ScopeStencilRenderType} marks the real draw, and the GlCommandEncoder mixin calls
 * {@link #prepareCurrentDraw()} immediately before {@code glDraw*}.</p>
 *
 * <p>The preferred path attaches a shared {@code GL_STENCIL_INDEX8} renderbuffer without touching depth.
 * AMD's OpenGL driver can reject DEPTH32 + standalone STENCIL8 with {@code GL_FRAMEBUFFER_UNSUPPORTED}; the
 * compatibility path therefore attaches a shared packed {@code GL_DEPTH24_STENCIL8} renderbuffer only for the
 * scope draw, then restores the exact original depth attachment. Unlike redefining the depth texture in place,
 * this does not mutate resources owned by vanilla or Iris and remains valid across shader reloads and window
 * resizes.</p>
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

    private static int sharedPackedRenderbuffer;
    private static int sharedPackedWidth;
    private static int sharedPackedHeight;

    /** Non-null only while the packed compatibility attachment is installed for the current draw. */
    private static DepthAttachmentRestore activeDepthRestore;

    private static boolean currentDrawPrepared;
    private static boolean currentDrawAllowed = true;
    private static String lastActiveSignature = "";
    private static String lastUnavailableReason = "";

    private ScopeStencilState() {
    }

    public static void begin(Phase phase) {
        RenderSystem.assertOnRenderThread();
        CURRENT_PHASE.set(phase);
        currentDrawPrepared = false;
        currentDrawAllowed = true;
        activeDepthRestore = null;
    }

    /**
     * Called by the command-encoder mixin after the destination framebuffer and shader are bound.
     *
     * @return whether the pending {@code drawFromBuffers} should execute. If both stencil attachment paths
     * fail, mask/reticle draws are skipped while the outside/body draw remains enabled as a conservative
     * fallback.
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
                if (activeDepthRestore != null) {
                    // The temporary packed target has no world depth. Initialise it once for the sequence;
                    // body and reticle draws reuse both its depth and stencil contents.
                    GL11.glDepthMask(true);
                    GL11.glClearDepth(1.0D);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
                    // The mask pipeline has writeDepth=false. Keep GL state aligned with GlCommandEncoder's cache.
                    GL11.glDepthMask(false);
                } else {
                    GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                }
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

        restoreOriginalDepthAttachment();

        currentDrawPrepared = false;
        currentDrawAllowed = true;
        CURRENT_PHASE.set(Phase.NONE);
    }

    private static boolean ensureStencilAttachment() {
        int fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (fbo == 0) {
            return GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0;
        }

        int existingStencilType = attachmentParameter(
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (existingStencilType != GL11.GL_NONE) {
            int existingStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (existingStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
                return true;
            }
            // Resize/reload can leave a stale attachment object on a reused FBO. Never trust objectType alone.
            detachStencilAttachment();
        }

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int width = Math.max(1, viewport[2]);
        int height = Math.max(1, viewport[3]);

        int separateStatus = tryAttachSeparateStencil(width, height);
        if (separateStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
            logActive("separate", fbo, width, height,
                    "separate stencil");
            return true;
        }
        detachStencilAttachment();

        int packedStatus = tryAttachTemporaryPackedDepthStencil(fbo, width, height);
        if (packedStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
            logActive("packed", fbo, width, height,
                    "temporary packed depth-stencil, separateStatus=0x"
                            + Integer.toHexString(separateStatus));
            return true;
        }

        logUnavailable("framebuffer " + fbo + " rejected separate stencil (0x"
                + Integer.toHexString(separateStatus) + ") and temporary packed depth-stencil (0x"
                + Integer.toHexString(packedStatus) + ")");
        return false;
    }

    /** @return framebuffer status after attaching a standalone stencil renderbuffer. */
    private static int tryAttachSeparateStencil(int width, int height) {
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
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    }

    /** @return status after replacing depth+stencil only for the current scope draw. */
    private static int tryAttachTemporaryPackedDepthStencil(int fbo, int width, int height) {
        DepthAttachmentRestore originalDepth = captureDepthAttachment(fbo);

        if (sharedPackedRenderbuffer == 0 || !GL30.glIsRenderbuffer(sharedPackedRenderbuffer)) {
            sharedPackedRenderbuffer = GL30.glGenRenderbuffers();
            sharedPackedWidth = 0;
            sharedPackedHeight = 0;
        }

        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, sharedPackedRenderbuffer);
        if (sharedPackedWidth != width || sharedPackedHeight != height) {
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            sharedPackedWidth = width;
            sharedPackedHeight = height;
        }
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                sharedPackedRenderbuffer
        );
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            activeDepthRestore = originalDepth;
            return status;
        }

        restoreDepthAttachment(originalDepth);
        activeDepthRestore = null;
        return status;
    }

    private static DepthAttachmentRestore captureDepthAttachment(int fbo) {
        int type = attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        int name = type == GL11.GL_NONE ? 0 : attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );
        int level = type == GL11.GL_TEXTURE ? attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
        ) : 0;
        return new DepthAttachmentRestore(fbo, type, name, level);
    }

    private static int attachmentParameter(int attachment, int parameter) {
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, attachment, parameter);
    }

    private static void detachStencilAttachment() {
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                0
        );
    }

    private static void restoreOriginalDepthAttachment() {
        DepthAttachmentRestore restore = activeDepthRestore;
        if (restore == null) {
            return;
        }

        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (previousFbo != restore.fbo()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restore.fbo());
        }
        restoreDepthAttachment(restore);
        int restoreStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (restoreStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
            logUnavailable("failed to restore framebuffer " + restore.fbo() + " after packed scope draw (0x"
                    + Integer.toHexString(restoreStatus) + ")");
        }
        if (previousFbo != restore.fbo()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        }
        activeDepthRestore = null;
    }

    private static void restoreDepthAttachment(DepthAttachmentRestore restore) {
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                0
        );

        if (restore.objectType() == GL30.GL_RENDERBUFFER) {
            GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    restore.objectName()
            );
        } else if (restore.objectType() == GL11.GL_TEXTURE) {
            GL32.glFramebufferTexture(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    restore.objectName(),
                    restore.textureLevel()
            );
        }
    }

    private static void logActive(String path, int fbo, int width, int height, String details) {
        String signature = path + ':' + fbo + ':' + width + 'x' + height;
        if (!signature.equals(lastActiveSignature)) {
            lastActiveSignature = signature;
            GunMod.LOGGER.info("[TACZ Scope] Draw-time stencil clipping active ({}, fbo={}, size={}x{}).",
                    details, fbo, width, height);
        }
    }

    private static void logUnavailable(String reason) {
        if (!reason.equals(lastUnavailableReason)) {
            lastUnavailableReason = reason;
            GunMod.LOGGER.warn("[TACZ Scope] Stencil clipping unavailable: {}. "
                    + "Keeping the ordinary body fallback.", reason);
        }
    }

    private record DepthAttachmentRestore(int fbo, int objectType, int objectName, int textureLevel) {
    }
}
