package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
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
 * <p>The preferred path attaches one shared {@code GL_STENCIL_INDEX8} renderbuffer without touching the
 * existing depth attachment. Some drivers reject a separate DEPTH32 + STENCIL8 combination with
 * {@code GL_FRAMEBUFFER_UNSUPPORTED}; in that case the existing mutable depth texture is promoted in-place to
 * upstream's {@code GL_DEPTH24_STENCIL8} format while retaining its object ID. A temporary packed renderbuffer,
 * restored after each draw, remains as the final compatibility path for non-texture/unsupported depth targets.</p>
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
    /** True when the current FBO's depth texture was reallocated to the upstream packed format. */
    private static boolean activeDepthWasReallocated;

    private static boolean currentDrawPrepared;
    private static boolean currentDrawAllowed = true;
    private static boolean loggedSeparateActive;
    private static boolean loggedPromotedDepthActive;
    private static boolean loggedPackedActive;
    private static boolean loggedUnavailable;

    private ScopeStencilState() {
    }

    public static void begin(Phase phase) {
        RenderSystem.assertOnRenderThread();
        CURRENT_PHASE.set(phase);
        currentDrawPrepared = false;
        currentDrawAllowed = true;
        activeDepthRestore = null;
        activeDepthWasReallocated = false;
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
                if (activeDepthRestore != null || activeDepthWasReallocated) {
                    // A temporary packed attachment is isolated from world depth, while upstream-style texture
                    // promotion reallocates storage once. Initialise depth for that first scope draw.
                    GL11.glDepthMask(true);
                    GL11.glClearDepth(1.0D);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
                    // The mask pipeline has writeDepth=false. Keep actual GL state aligned with its cached state.
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

        activeDepthWasReallocated = false;
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

        int objectType = attachmentParameter(
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

        int separateStatus = tryAttachSeparateStencil(fbo, width, height);
        if (separateStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedSeparateActive) {
                loggedSeparateActive = true;
                GunMod.LOGGER.info("[TACZ Scope] Draw-time stencil clipping active "
                        + "(separate stencil, fbo={}, size={}x{}).", fbo, width, height);
            }
            return true;
        }

        // Leave the original framebuffer exactly as it was before trying the two packed compatibility paths.
        GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                0
        );

        // This is the same strategy used upstream for OptiFine: preserve the depth texture object ID but
        // redefine its mutable storage as DEPTH24_STENCIL8, then attach both aspects. Vanilla 26.1.2 creates
        // mutable DEPTH32 textures, so the operation is legal and all RenderTarget/Iris references remain valid.
        int promotionStatus = tryPromoteDepthTextureToPacked(fbo);
        if (promotionStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedPromotedDepthActive) {
                loggedPromotedDepthActive = true;
                GunMod.LOGGER.info("[TACZ Scope] Draw-time stencil clipping active "
                                + "(upstream packed depth texture, fbo={}, separateStatus=0x{}).",
                        fbo, Integer.toHexString(separateStatus));
            }
            return true;
        }

        int packedStatus = tryAttachPackedDepthStencil(fbo, width, height);
        if (packedStatus == GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedPackedActive) {
                loggedPackedActive = true;
                GunMod.LOGGER.info("[TACZ Scope] Draw-time stencil clipping active "
                                + "(temporary packed depth-stencil, fbo={}, size={}x{}, separateStatus=0x{}, "
                                + "promotionStatus=0x{}).",
                        fbo, width, height, Integer.toHexString(separateStatus),
                        Integer.toHexString(promotionStatus));
            }
            return true;
        }

        logUnavailable("framebuffer " + fbo + " rejected separate stencil (0x"
                + Integer.toHexString(separateStatus) + "), depth texture promotion (0x"
                + Integer.toHexString(promotionStatus) + ") and temporary packed depth-stencil (0x"
                + Integer.toHexString(packedStatus) + ")");
        return false;
    }

    /** @return framebuffer status after attaching a standalone stencil renderbuffer. */
    private static int tryAttachSeparateStencil(int fbo, int width, int height) {
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

    /**
     * Promotes a mutable DEPTH32 texture to DEPTH24_STENCIL8 without changing its OpenGL object ID.
     * This mirrors upstream's OptiFine compatibility path.
     */
    private static int tryPromoteDepthTextureToPacked(int fbo) {
        int type = attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (type != GL11.GL_TEXTURE) {
            return GL30.GL_FRAMEBUFFER_UNSUPPORTED;
        }

        int texture = attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );
        int level = attachmentParameter(
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
        );
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        int internalFormat = GL11.glGetTexLevelParameteri(
                GL11.GL_TEXTURE_2D,
                level,
                GL11.GL_TEXTURE_INTERNAL_FORMAT
        );
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);

        boolean reallocated = false;
        if (internalFormat != GL30.GL_DEPTH24_STENCIL8) {
            int depthType = GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D,
                    level,
                    GL30.GL_TEXTURE_DEPTH_TYPE
            );
            if (depthType != GL30.GL_UNSIGNED_NORMALIZED || width <= 0 || height <= 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                return GL30.GL_FRAMEBUFFER_UNSUPPORTED;
            }

            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    level,
                    GL30.GL_DEPTH24_STENCIL8,
                    width,
                    height,
                    0,
                    GL30.GL_DEPTH_STENCIL,
                    GL30.GL_UNSIGNED_INT_24_8,
                    (java.nio.ByteBuffer) null
            );
            reallocated = true;
        }

        GL32.glFramebufferTexture(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                texture,
                level
        );
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            activeDepthWasReallocated = reallocated;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            return status;
        }

        // Restore the declared vanilla format if the promoted attachment is not supported either.
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, 0, 0);
        if (reallocated) {
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    level,
                    GL14.GL_DEPTH_COMPONENT32,
                    width,
                    height,
                    0,
                    GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT,
                    (java.nio.ByteBuffer) null
            );
        }
        GL32.glFramebufferTexture(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                texture,
                level
        );
        activeDepthWasReallocated = false;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        return status;
    }

    /** @return framebuffer status after temporarily replacing depth with a packed depth-stencil renderbuffer. */
    private static int tryAttachPackedDepthStencil(int fbo, int width, int height) {
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

        // The compatibility attachment also failed. Restore before allowing the ordinary body fallback to draw.
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
        if (previousFbo != restore.fbo()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        }
        activeDepthRestore = null;
    }

    private static void restoreDepthAttachment(DepthAttachmentRestore restore) {
        // Detach the temporary packed object from both depth and stencil attachment points.
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

    private static void logUnavailable(String reason) {
        if (!loggedUnavailable) {
            loggedUnavailable = true;
            GunMod.LOGGER.warn("[TACZ Scope] Stencil clipping unavailable: {}. "
                    + "Keeping the ordinary body fallback.", reason);
        }
    }

    private record DepthAttachmentRestore(int fbo, int objectType, int objectName, int textureLevel) {
    }
}
