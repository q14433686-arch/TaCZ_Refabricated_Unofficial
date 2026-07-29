package com.tacz.guns.compat.iris;

import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime bridge for the Iris HAND shader scope-mask experiment.
 *
 * <p>This class deliberately uses reflection for Iris/Minecraft OpenGL internals so the normal build does
 * not acquire a hard Iris dependency.</p>
 */
public final class IrisScopeMaskState {
    private static final String BODY_PIPELINE = "pipeline/scope_body_clipped";
    private static final String RETICLE_PIPELINE = "pipeline/scope_reticle_clipped";
    private static final String RETICLE_EMISSIVE_PIPELINE = "pipeline/scope_reticle_emissive_clipped";
    private static final String MASK_SAMPLER = "ScopeMaskSampler";
    private static final String UNIFORM_MODE = "tacz_ScopeMaskMode";
    private static final String UNIFORM_SAMPLER = "tacz_ScopeMaskSampler";

    private static final ThreadLocal<Integer> MODE = ThreadLocal.withInitial(() -> 0);
    private static boolean loggedFailure;
    private static boolean loggedApply;

    private IrisScopeMaskState() {
    }

    public static void captureRenderPass(Object glRenderPass) {
        MODE.set(resolveMode(glRenderPass));
    }

    public static void applyToShaderProgram(Object shader, Map<?, ?> samplers) {
        int programId = getProgramId(shader);
        if (programId <= 0) {
            return;
        }
        applyToActiveProgram(programId, samplers);
    }

    public static void applyToActiveProgram(int programId, Map<?, ?> samplers) {
        int mode = MODE.get();
        if (mode == 0) {
            setMode(programId, 0);
            return;
        }
        Object textureViewAndSampler = samplers == null ? null : samplers.get(MASK_SAMPLER);
        if (textureViewAndSampler == null) {
            setMode(programId, 0);
            return;
        }
        int textureId = getGlTextureId(textureViewAndSampler);
        if (textureId <= 0) {
            setMode(programId, 0);
            return;
        }

        int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
        int samplerLocation = GL20C.glGetUniformLocation(programId, UNIFORM_SAMPLER);
        if (modeLocation < 0 || samplerLocation < 0) {
            return;
        }

        int unit = Math.max(3, GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS) - 1);
        if (!loggedApply) {
            loggedApply = true;
            GunMod.LOGGER.info("[TACZ Scope] Iris scope-mask bridge active (mode={}, textureUnit={}).", mode, unit);
        }
        GL20C.glUniform1i(modeLocation, mode);
        GL20C.glUniform1i(samplerLocation, unit);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureId);
    }

    private static void setMode(int programId, int mode) {
        int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
        if (modeLocation >= 0) {
            GL20C.glUniform1i(modeLocation, mode);
        }
    }

    private static int resolveMode(Object glRenderPass) {
        try {
            if (glRenderPass == null) {
                return 0;
            }
            Object glPipeline = readField(glRenderPass, "pipeline");
            if (glPipeline == null) {
                return 0;
            }
            Object renderPipeline = invokeNoArgs(glPipeline, "info");
            if (renderPipeline == null) {
                return 0;
            }
            Object location = invokeNoArgs(renderPipeline, "getLocation");
            if (location == null) {
                return 0;
            }
            String namespace = String.valueOf(invokeNoArgs(location, "getNamespace"));
            String path = String.valueOf(invokeNoArgs(location, "getPath"));
            if (!GunMod.MOD_ID.equals(namespace)) {
                return 0;
            }
            String normalized = path.toLowerCase(Locale.ROOT);
            if (BODY_PIPELINE.equals(normalized)) {
                return 1;
            }
            if (RETICLE_PIPELINE.equals(normalized) || RETICLE_EMISSIVE_PIPELINE.equals(normalized)) {
                return 2;
            }
        } catch (Throwable t) {
            logOnce("resolve scope render pass", t);
        }
        return 0;
    }

    private static int getProgramId(Object shader) {
        try {
            if (shader == null) {
                return 0;
            }
            Method method = null;
            for (Class<?> c = shader.getClass(); c != null && method == null; c = c.getSuperclass()) {
                try {
                    method = c.getDeclaredMethod("getProgramId");
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (method == null) {
                return 0;
            }
            method.setAccessible(true);
            Object id = method.invoke(shader);
            if (id instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable t) {
            logOnce("resolve Iris shader program id", t);
        }
        return 0;
    }

    private static int getGlTextureId(Object textureViewAndSampler) {
        try {
            Object view = invokeNoArgs(textureViewAndSampler, "view");
            if (view == null) {
                return 0;
            }
            Object texture = invokeNoArgs(view, "texture");
            if (texture == null) {
                return 0;
            }
            Object id = invokeNoArgs(texture, "iris$getGlId");
            if (id instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable t) {
            logOnce("resolve scope mask texture", t);
        }
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

    private static void logOnce(String action, Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            GunMod.LOGGER.warn("[TACZ Scope] Iris scope-mask bridge failed to {}. Scope clipping will fall back for this draw.", action, t);
        }
    }
}
