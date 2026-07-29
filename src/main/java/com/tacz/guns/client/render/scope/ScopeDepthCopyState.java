package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * Saves world depth before the ocular aperture and exposes that copy to the ordered cleanup geometry.
 * The cleanup fragment writes the sampled value to gl_FragDepth only where ocular geometry rasterizes.
 */
public final class ScopeDepthCopyState {
    public enum Operation {
        NONE,
        BACKUP,
        RESTORE
    }

    public static final String MODE_UNIFORM = "tacz_DepthRestoreMode";
    public static final String SAMPLER_UNIFORM = "tacz_DepthBackupSampler";
    public static final String IRIS_WORLD_DEPTH_UNIFORM = "depthtex2";

    private static final ThreadLocal<Operation> CURRENT = ThreadLocal.withInitial(() -> Operation.NONE);

    private static int backupFramebuffer;
    private static int backupTexture;
    private static int backupWidth;
    private static int backupHeight;
    private static int backupInternalFormat;
    private static int backupSourceFbo;
    private static boolean backupValid;
    private static boolean useIrisPreHandDepth;
    private static boolean loggedIrisPreHandDepth;

    private static int overriddenTextureUnit = -1;
    private static int previousTextureBinding;
    private static boolean loggedActive;
    private static String lastFailure = "";

    private ScopeDepthCopyState() {
    }

    public static void begin(Operation operation) {
        RenderSystem.assertOnRenderThread();
        CURRENT.set(operation);
    }

    /** @return whether GlCommandEncoder should execute the pending draw. */
    public static boolean beforeDraw() {
        RenderSystem.assertOnRenderThread();
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        return switch (CURRENT.get()) {
            case BACKUP -> {
                disableRestoreMode(program);
                if (program > 0
                        && GL20.glGetUniformLocation(program, MODE_UNIFORM) >= 0
                        && GL20.glGetUniformLocation(program, IRIS_WORLD_DEPTH_UNIFORM) >= 0) {
                    // Iris copies exact world depth before HAND_SOLID into depthtex2.
                    useIrisPreHandDepth = true;
                    backupValid = true;
                    if (!loggedIrisPreHandDepth) {
                        loggedIrisPreHandDepth = true;
                        GunMod.LOGGER.info("[TACZ Scope] Using Iris depthtex2 as exact pre-hand depth backup.");
                    }
                } else {
                    useIrisPreHandDepth = false;
                    backupCurrentDepth();
                }
                yield true;
            }
            case RESTORE -> prepareRestoreDraw(program);
            case NONE -> {
                disableRestoreMode(program);
                yield true;
            }
        };
    }

    public static void end() {
        if (overriddenTextureUnit >= 0) {
            int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + overriddenTextureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
            GL13.glActiveTexture(previousActiveTexture);
            overriddenTextureUnit = -1;
        }
        CURRENT.set(Operation.NONE);
    }

    private static void disableRestoreMode(int program) {
        if (program <= 0) {
            return;
        }
        int modeLocation = GL20.glGetUniformLocation(program, MODE_UNIFORM);
        if (modeLocation >= 0) {
            GL20.glUniform1i(modeLocation, 0);
        }
    }

    private static boolean backupCurrentDepth() {
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        DepthInfo depth = inspectDepthAttachment();
        if (sourceFbo == 0 || depth == null || depth.samples() != 0 || !ensureBackupTarget(depth)) {
            backupValid = false;
            logFailure("cannot prepare sampleable depth backup for fbo=" + sourceFbo);
            return false;
        }

        clearGlErrors();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, backupFramebuffer);
        GL30.glBlitFramebuffer(
                0, 0, depth.width(), depth.height(),
                0, 0, depth.width(), depth.height(),
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        int error = GL11.glGetError();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        backupValid = error == GL11.GL_NO_ERROR;
        backupSourceFbo = backupValid ? sourceFbo : 0;
        if (!backupValid) {
            logFailure("depth backup blit failed with GL error 0x" + Integer.toHexString(error));
        } else if (!loggedActive) {
            loggedActive = true;
            GunMod.LOGGER.info("[TACZ Scope] Exact ocular depth backup active (fbo={}, size={}x{}, format=0x{}).",
                    sourceFbo, depth.width(), depth.height(), Integer.toHexString(depth.internalFormat()));
        }
        return backupValid;
    }

    private static boolean prepareRestoreDraw(int program) {
        if (!backupValid || program <= 0) {
            return false;
        }

        int modeLocation = GL20.glGetUniformLocation(program, MODE_UNIFORM);
        if (useIrisPreHandDepth) {
            int irisDepthLocation = GL20.glGetUniformLocation(program, IRIS_WORLD_DEPTH_UNIFORM);
            if (modeLocation < 0 || irisDepthLocation < 0) {
                logFailure("Iris cleanup shader has no depthtex2 restore branch");
                return false;
            }
            // Iris' ProgramSamplers has already bound depthtex2 to the pre-hand depth copy.
            GL20.glUniform1i(modeLocation, 1);
            backupValid = false;
            return true;
        }

        int destinationFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        DepthInfo destination = inspectDepthAttachment();
        if (destinationFbo != backupSourceFbo || destination == null
                || destination.width() != backupWidth
                || destination.height() != backupHeight
                || destination.internalFormat() != backupInternalFormat) {
            backupValid = false;
            logFailure("depth restore target does not match the backed-up hand target");
            return false;
        }

        int samplerLocation = GL20.glGetUniformLocation(program, SAMPLER_UNIFORM);
        if (modeLocation < 0 || samplerLocation < 0) {
            logFailure("active cleanup shader has no depth-restore uniforms");
            return false;
        }

        int textureUnit = Math.max(3, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) - 1);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backupTexture);
        GL13.glActiveTexture(previousActiveTexture);
        overriddenTextureUnit = textureUnit;

        GL20.glUniform1i(samplerLocation, textureUnit);
        GL20.glUniform1i(modeLocation, 1);
        backupValid = false;
        return true;
    }

    private static boolean ensureBackupTarget(DepthInfo depth) {
        if (backupFramebuffer == 0 || !GL30.glIsFramebuffer(backupFramebuffer)) {
            backupFramebuffer = GL30.glGenFramebuffers();
        }
        if (backupTexture == 0 || !GL11.glIsTexture(backupTexture)) {
            backupTexture = GL11.glGenTextures();
            backupWidth = 0;
            backupHeight = 0;
            backupInternalFormat = 0;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backupTexture);
        if (backupWidth != depth.width()
                || backupHeight != depth.height()
                || backupInternalFormat != depth.internalFormat()) {
            TextureAllocation allocation = textureAllocation(depth.internalFormat());
            if (allocation == null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                return false;
            }
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    depth.internalFormat(),
                    depth.width(),
                    depth.height(),
                    0,
                    allocation.externalFormat(),
                    allocation.type(),
                    (java.nio.ByteBuffer) null
            );
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            backupWidth = depth.width();
            backupHeight = depth.height();
            backupInternalFormat = depth.internalFormat();
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, backupFramebuffer);
        GL32.glFramebufferTexture(
                GL30.GL_FRAMEBUFFER,
                isPackedDepthStencil(depth.internalFormat())
                        ? GL30.GL_DEPTH_STENCIL_ATTACHMENT
                        : GL30.GL_DEPTH_ATTACHMENT,
                backupTexture,
                0
        );
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        return status == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    private static DepthInfo inspectDepthAttachment() {
        int type = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (type == GL11.GL_NONE) {
            return null;
        }
        int name = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );

        if (type == GL11.GL_TEXTURE) {
            int level = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
            );
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
            int format = GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            return width > 0 && height > 0 ? new DepthInfo(width, height, format, 0) : null;
        }

        if (type == GL30.GL_RENDERBUFFER) {
            int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, name);
            int width = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
            int height = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
            int format = GL30.glGetRenderbufferParameteri(
                    GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            int samples = GL30.glGetRenderbufferParameteri(
                    GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_SAMPLES);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            return width > 0 && height > 0 ? new DepthInfo(width, height, format, samples) : null;
        }
        return null;
    }

    private static TextureAllocation textureAllocation(int internalFormat) {
        if (internalFormat == GL30.GL_DEPTH24_STENCIL8) {
            return new TextureAllocation(GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8);
        }
        if (internalFormat == GL32.GL_DEPTH32F_STENCIL8) {
            return new TextureAllocation(GL30.GL_DEPTH_STENCIL, GL32.GL_FLOAT_32_UNSIGNED_INT_24_8_REV);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT16) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT24) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT32 || internalFormat == GL30.GL_DEPTH_COMPONENT32F) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        }
        return null;
    }

    private static boolean isPackedDepthStencil(int internalFormat) {
        return internalFormat == GL30.GL_DEPTH24_STENCIL8
                || internalFormat == GL32.GL_DEPTH32F_STENCIL8;
    }

    private static void clearGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain stale errors so blit diagnostics are attributable
        }
    }

    private static void logFailure(String reason) {
        if (!reason.equals(lastFailure)) {
            lastFailure = reason;
            GunMod.LOGGER.warn("[TACZ Scope] {}", reason);
        }
    }

    private record DepthInfo(int width, int height, int internalFormat, int samples) {
    }

    private record TextureAllocation(int externalFormat, int type) {
    }
}
