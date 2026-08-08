package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the ocular screen-space mask rendering lifecycle.
 *
 * <p>Replaces the old {@code ScopeDepthCopyState} which manipulated the depth buffer
 * (BACKUP / APERTURE_COPY / RESTORE / MASK). The new approach is simpler:
 * <ol>
 *   <li>At the phase boundary, render ocular geometry to an offscreen RGBA8 FBO
 *       ({@link ScopeMaskRenderer} / {@link ScopeMaskTarget}).</li>
 *   <li>Scope body / reticle shaders sample this mask texture for per-pixel discard.</li>
 *   <li>The main depth buffer is <b>never</b> touched — no fog/SSAO/SSR ghost-hand artifacts.</li>
 * </ol>
 *
 * <h2>Vanilla vs Iris path</h2>
 * <ul>
 *   <li><b>Vanilla</b>: Body/reticle use custom {@code scope_body.fsh} with {@code SCOPE_MASK} define.
 *       The mask texture is bound through {@link ScopeBodyRenderTypes} render setup.
 *       The encoder mixin zeroes {@code tacz_ScopeMaskMode} for non-scope draws.</li>
 *   <li><b>Iris</b>: Iris replaces shaders. {@link IrisShaderCreatorMixin} injects a dormant mask
 *       branch into ALL Iris shaders. {@link IrisScopeMaskState} activates it per-draw
 *       via the {@code GlCommandEncoder#trySetup} hook.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskState {

    private static final ThreadLocal<Boolean> MASK_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Texture unit for the mask sampler. High value to avoid conflicts with vanilla/Iris samplers. */
    private static int maskTextureUnit = -1;

    private static final List<Runnable> CLEANUP = new ArrayList<>();

    private ScopeMaskState() {}

    /** Begin a mask-aware draw (called by DepthCopyRenderType wrapper). */
    public static void beginMaskDraw() {
        MASK_ACTIVE.set(Boolean.TRUE);
    }

    /** End a mask-aware draw. */
    public static void endMaskDraw() {
        MASK_ACTIVE.set(Boolean.FALSE);
        // Run any registered cleanup
        for (int i = CLEANUP.size() - 1; i >= 0; i--) {
            CLEANUP.get(i).run();
        }
        CLEANUP.clear();
    }

    public static boolean isMaskActive() {
        return MASK_ACTIVE.get();
    }

    /**
     * Gets or computes the texture unit to use for the mask sampler.
     * Uses the highest available unit to avoid conflicts.
     */
    public static int getMaskTextureUnit() {
        if (maskTextureUnit < 0) {
            maskTextureUnit = Math.max(8, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) - 2);
        }
        return maskTextureUnit;
    }

    /**
     * Sets scope mask uniforms on the current GL program.
     *
     * @param program GL program id
     * @param mode 0=off, 1=body discard-inside, 2=reticle discard-outside
     */
    public static void setMaskUniforms(int program, int mode) {
        if (program <= 0) return;
        int modeLoc = GL20.glGetUniformLocation(program, "tacz_ScopeMaskMode");
        if (modeLoc >= 0) {
            GL20.glUniform1i(modeLoc, mode);
        }
        if (mode > 0) {
            int samplerLoc = GL20.glGetUniformLocation(program, "ScopeMaskSampler");
            if (samplerLoc >= 0) {
                GL20.glUniform1i(samplerLoc, getMaskTextureUnit());
            }
        }
    }

    /**
     * Binds the mask FBO's color texture to the designated texture unit.
     * Saves and restores the previous active texture unit.
     */
    public static void bindMaskTexture() {
        if (!ScopeMaskTarget.isAvailable()) return;
        int unit = getMaskTextureUnit();
        int prevUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ScopeMaskTarget.colorTexture());
        GL13.glActiveTexture(prevUnit);
        // Register cleanup to restore previous binding
        CLEANUP.add(() -> {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL13.glActiveTexture(prevUnit);
        });
    }

    /**
     * Zeros scope mask uniforms on the current program.
     * Called by the encoder mixin for non-scope draws to prevent stale uniform leakage.
     */
    public static void zeroMaskUniforms() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program <= 0) return;
        int modeLoc = GL20.glGetUniformLocation(program, "tacz_ScopeMaskMode");
        if (modeLoc >= 0) {
            GL20.glUniform1i(modeLoc, 0);
        }
    }

    /**
     * Current ADS aiming progress (0=hipfire, 1=fully aimed).
     * Written into the mask's green channel for edge softening.
     */
    public static float currentAimingProgress() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0.0f;
        try {
            var gunOp = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player);
            return Mth.clamp(
                    gunOp.getClientAimingProgress(
                            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)),
                    0.0f, 1.0f);
        } catch (Exception e) {
            return 0.0f;
        }
    }
}
