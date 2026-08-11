package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime bridge for the Iris HAND shader scope-mask integration.
 *
 * <p>This class manages the per-draw uniform state for patched Iris shaders so that
 * custom scope clipping runs accurately when scope body or reticle passes are submitted,
 * while all standard passes (gun body, attachments, player hands, entities) are explicitly
 * set to {@code mode = 0} on every draw call to prevent uniform leakage and random clipping.</p>
 */
public final class IrisScopeMaskState {
    private static final String BODY_PIPELINE = "pipeline/scope_body_clipped";
    private static final String FLASH_TRANSLUCENT_PIPELINE = "pipeline/scope_flash_translucent_clipped";
    private static final String FLASH_SWIRL_PIPELINE = "pipeline/scope_flash_swirl_clipped";
    private static final String RETICLE_PIPELINE = "pipeline/scope_reticle_clipped";
    private static final String RETICLE_EMISSIVE_PIPELINE = "pipeline/scope_reticle_emissive_clipped";
    private static final String MASK_SAMPLER = "ScopeMaskSampler";
    private static final String UNIFORM_MODE = "tacz_ScopeMaskMode";
    private static final String UNIFORM_SAMPLER = "tacz_ScopeMaskSampler";

    private static boolean loggedFailure;
    private static boolean loggedApply;

    private IrisScopeMaskState() {
    }

    /**
     * Resets the scope mask uniform on an Iris ExtendedShader program to 0 upon setup/binding.
     */
    public static void resetShaderProgram(Object shader) {
        try {
            int programId = getProgramId(shader);
            if (programId > 0) {
                int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
                if (modeLocation >= 0) {
                    GL20C.glUniform1i(modeLocation, 0);
                }
            }
        } catch (Throwable t) {
            logOnce("reset shader program", t);
        }
    }

    /**
     * Updates the active Iris shader program uniforms for the current GlRenderPass draw call.
     * If the draw call is {@code scope_body_clipped}, mode is set to 1.
     * If the draw call is {@code scope_reticle_clipped}, mode is set to 2.
     * Otherwise (gun body, attachments, hands, entities, particles), mode is set to 0.
     */
    public static void applyToGlRenderPass(Object glRenderPass) {
        try {
            if (glRenderPass == null) {
                return;
            }
            int mode = resolveMode(glRenderPass);

            int programId = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if (programId <= 0) {
                Object glPipeline = readField(glRenderPass, "pipeline");
                if (glPipeline != null) {
                    Object glProg = invokeNoArgs(glPipeline, "program");
                    programId = getProgramId(glProg);
                }
            }
            if (programId <= 0) {
                return;
            }

            int modeLocation = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);
            if (modeLocation < 0) {
                return;
            }

            if (mode == 0) {
                GL20C.glUniform1i(modeLocation, 0);
                return;
            }

            int samplerLocation = GL20C.glGetUniformLocation(programId, UNIFORM_SAMPLER);
            if (samplerLocation < 0) {
                GL20C.glUniform1i(modeLocation, 0);
                return;
            }

            int textureId = resolveMaskTextureId(glRenderPass);
            if (textureId <= 0) {
                GL20C.glUniform1i(modeLocation, 0);
                return;
            }

            int maxUnits = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
            int unit = Math.max(15, maxUnits - 1);
            if (!loggedApply) {
                loggedApply = true;
                GunMod.LOGGER.info("[TACZ Scope] Iris scope-mask bridge active (mode={}, textureUnit={}, textureId={}).", mode, unit, textureId);
            }

            GL20C.glUniform1i(modeLocation, mode);
            GL20C.glUniform1i(samplerLocation, unit);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureId);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        } catch (Throwable t) {
            logOnce("apply scope mask to GL render pass", t);
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
            if (BODY_PIPELINE.equals(normalized) || FLASH_TRANSLUCENT_PIPELINE.equals(normalized)
                    || FLASH_SWIRL_PIPELINE.equals(normalized)) {
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

    private static int resolveMaskTextureId(Object glRenderPass) {
        try {
            Object samplersObj = readField(glRenderPass, "samplers");
            if (samplersObj instanceof Map<?, ?> samplers) {
                Object tvs = samplers.get(MASK_SAMPLER);
                if (tvs != null) {
                    int id = getGlTextureId(tvs);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            RenderTarget target = ScopeMaskTarget.current();
            if (target != null) {
                Object colorTex = target.getColorTexture();
                if (colorTex != null) {
                    int id = getGlTextureId(colorTex);
                    if (id > 0) {
                        return id;
                    }
                }
                Object colorTexView = target.getColorTextureView();
                if (colorTexView != null) {
                    int id = getGlTextureId(colorTexView);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
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
            logOnce("resolve shader program id", t);
        }
        return 0;
    }

    private static int getGlTextureId(Object obj) {
        if (obj == null) {
            return 0;
        }
        try {
            if (obj.getClass().getSimpleName().contains("TextureViewAndSampler")) {
                Object view = invokeNoArgs(obj, "view");
                return getGlTextureId(view);
            }
            try {
                Method glIdMethod = obj.getClass().getMethod("glId");
                glIdMethod.setAccessible(true);
                Object id = glIdMethod.invoke(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method irisGlIdMethod = obj.getClass().getMethod("iris$getGlId");
                irisGlIdMethod.setAccessible(true);
                Object id = irisGlIdMethod.invoke(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method textureMethod = obj.getClass().getMethod("texture");
                textureMethod.setAccessible(true);
                Object tex = textureMethod.invoke(obj);
                if (tex != null && tex != obj) {
                    int id = getGlTextureId(tex);
                    if (id > 0) {
                        return id;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Field idField = obj.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object id = idField.get(obj);
                if (id instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (NoSuchFieldException ignored) {
            }
        } catch (Throwable t) {
            logOnce("extract texture id", t);
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
