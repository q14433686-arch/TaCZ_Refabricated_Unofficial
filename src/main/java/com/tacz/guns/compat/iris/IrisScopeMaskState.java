package com.tacz.guns.compat.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskState;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Per-draw scope mask uniform bridge for the Iris rendering path.
 *
 * <p>When Iris replaces our custom scope_body shader with its own hand shader,
 * the {@code SCOPE_MASK} define in scope_body.fsh is irrelevant. Instead,
 * {@link com.tacz.guns.mixin.client.iris.IrisShaderCreatorMixin} injects a dormant
 * mask branch into ALL Iris shaders, and this class activates it per-draw
 * by setting {@code tacz_ScopeMaskMode} and binding {@code ScopeMaskSampler}.</p>
 *
 * <p>Called from {@code IrisGlCommandEncoderMixin} which hooks into
 * {@code GlCommandEncoder#trySetup} (Iris path only).</p>
 *
 * <h2>Pipeline detection</h2>
 * The mode is determined by inspecting the pipeline location:
 * <ul>
 *   <li>{@code tacz:pipeline/scope_body_clipped} → mode 1 (body, discard inside)</li>
 *   <li>{@code tacz:pipeline/scope_reticle_*_clipped} → mode 2 (reticle, discard outside)</li>
 *   <li>Anything else → mode 0 (no masking)</li>
 * </ul>
 *
 * <h2>Reflection</h2>
 * Uses reflection to access GlRenderPass.pipeline.info.location because the Blaze3D
 * internal types are not part of the public API.
 */
@Environment(EnvType.CLIENT)
public final class IrisScopeMaskState {

    private static final String BODY_PIPELINE = "pipeline/scope_body_clipped";
    private static final String RETICLE_PIPELINE = "pipeline/scope_reticle_clipped";
    private static final String RETICLE_EMISSIVE_PIPELINE = "pipeline/scope_reticle_emissive_clipped";

    private static boolean loggedFailure;
    private static boolean loggedApply;

    private IrisScopeMaskState() {}

    /**
     * Called on every Iris render pass setup. Inspects the pipeline and applies
     * the correct scope mask mode.
     */
    public static void applyToGlRenderPass(Object glRenderPass) {
        try {
            if (glRenderPass == null) return;

            int mode = resolveMode(glRenderPass);
            if (mode == 0) {
                // Non-scope draw: zero the mask uniform to prevent stale state
                int programId = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
                if (programId > 0) {
                    int loc = GL20C.glGetUniformLocation(programId, "tacz_ScopeMaskMode");
                    if (loc >= 0) GL20C.glUniform1i(loc, 0);
                }
                return;
            }

            int programId = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (programId <= 0) return;

            int modeLoc = GL20C.glGetUniformLocation(programId, "tacz_ScopeMaskMode");
            if (modeLoc < 0) return;

            if (!ScopeMaskTarget.isAvailable()) {
                GL20C.glUniform1i(modeLoc, 0);
                return;
            }

            int samplerLoc = GL20C.glGetUniformLocation(programId, "ScopeMaskSampler");
            if (samplerLoc < 0) {
                GL20C.glUniform1i(modeLoc, 0);
                return;
            }

            // Bind mask texture to high texture unit
            int unit = ScopeMaskState.getMaskTextureUnit();
            int texId = ScopeMaskTarget.colorTexture();
            if (texId <= 0) {
                GL20C.glUniform1i(modeLoc, 0);
                return;
            }

            GL20C.glUniform1i(modeLoc, mode);
            GL20C.glUniform1i(samplerLoc, unit);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texId);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);

            if (!loggedApply) {
                loggedApply = true;
                GunMod.LOGGER.info("[TACZ Scope] Iris scope mask bridge active (mode={}, unit={}, tex={}).",
                        mode, unit, texId);
            }
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Iris scope mask bridge failed; scope clipping disabled for this draw.", t);
            }
        }
    }

    /** Also reset scope mask state when Iris sets up an ExtendedShader program. */
    public static void resetShaderProgram(Object shader) {
        try {
            int programId = getProgramId(shader);
            if (programId > 0) {
                int loc = GL20C.glGetUniformLocation(programId, "tacz_ScopeMaskMode");
                if (loc >= 0) GL20C.glUniform1i(loc, 0);
            }
        } catch (Throwable ignored) {}
    }

    // ── Reflection helpers ─────────────────────────────────────────────

    private static int resolveMode(Object glRenderPass) {
        try {
            Object glPipeline = readField(glRenderPass, "pipeline");
            if (glPipeline == null) return 0;
            Object renderPipeline = invokeNoArgs(glPipeline, "info");
            if (renderPipeline == null) return 0;
            Object location = invokeNoArgs(renderPipeline, "getLocation");
            if (location == null) return 0;

            String namespace = String.valueOf(invokeNoArgs(location, "getNamespace"));
            String path = String.valueOf(invokeNoArgs(location, "getPath"));
            if (!GunMod.MOD_ID.equals(namespace)) return 0;

            String normalized = path.toLowerCase(java.util.Locale.ROOT);
            if (BODY_PIPELINE.equals(normalized)) return 1;
            if (RETICLE_PIPELINE.equals(normalized) || RETICLE_EMISSIVE_PIPELINE.equals(normalized)) return 2;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Failed to resolve scope pipeline mode", t);
            }
        }
        return 0;
    }

    private static int getProgramId(Object shader) {
        try {
            if (shader == null) return 0;
            Method method = null;
            for (Class<?> c = shader.getClass(); c != null && method == null; c = c.getSuperclass()) {
                try { method = c.getDeclaredMethod("getProgramId"); } catch (NoSuchMethodException ignored) {}
            }
            if (method == null) return 0;
            method.setAccessible(true);
            Object id = method.invoke(shader);
            if (id instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
        return 0;
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
