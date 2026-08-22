package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
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

    /**
     * {@code GlRenderPass.pipeline} 字段，按 class 缓存。
     *
     * <h3>为什么非缓存不可</h3>
     * {@link #applyToGlRenderPass} 挂在 {@code GlCommandEncoder.trySetup} 上，
     * 也就是<b>每一次 draw call 之前</b>都会跑一遍 —— 开着 Sodium + Iris，
     * 这是每帧成千上万次。原来那版每次都现查：
     * <pre>
     * target.getClass().getDeclaredField(name)   // 每次都新建一个 Field 副本
     * target.getClass().getMethod(name)          // 同上，且要走完整张公共方法表
     * </pre>
     * {@code getDeclaredField}/{@code getMethod} <b>每次调用都返回一份防御性拷贝</b>，
     * 于是每个 draw call 要付 5 次反射查找 + 5 次对象分配 + 5 次 setAccessible 访问检查。
     * 这笔钱与开不开镜无关，是<b>全程</b>都在付的。
     */
    private static Class<?> cachedPassClass;
    private static Field cachedPipelineField;
    private static boolean pipelineFieldResolved;

    /**
     * 「这套 GL 管线对应哪个 mode」的记忆。
     *
     * <p>一个 {@code GlRenderPipeline} 实例对应的 RenderPipeline location 是<b>固定</b>的，
     * 所以判定结果永远不变 —— 逐 draw call 重新用反射取一遍 location、
     * 再 {@code toLowerCase} 出一个新字符串来比较，纯属白花。
     * 按实例身份记住即可。
     */
    private static final java.util.Map<Object, Integer> MODE_BY_PIPELINE = new java.util.IdentityHashMap<>();
    /** 管线实例是有限的（几十个）；真出现异常增长就整体丢弃重来，避免无界增长。 */
    private static final int MODE_CACHE_LIMIT = 512;

    /** {@code GL_MAX_TEXTURE_IMAGE_UNITS} 是驱动常量，问一次就够。 */
    private static int cachedMaxTextureUnits = -1;

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
            // 【快速路径 —— 本方法每次 draw call 都会被调到】
            //
            // mode 只可能在「本帧画了目镜掩码」的帧上变成非 0。既没开镜、上一帧也没开镜，
            // 就不存在任何需要写的 uniform，也不存在需要擦掉的残留 —— 直接回。
            //
            // 为什么「上一帧」也要算进去：Iris 把我们的 scope_body / scope_reticle 管线
            // 映射到它的 HAND 程序上，也就是<b>同一个 GL program</b> 既画镜身（mode=1）
            // 也画枪和手（mode=0）。松开右键的<b>那一帧</b>必须照常跑完整流程，
            // 把这些程序里残留的 mode 擦回 0，否则枪身会带着上一帧的裁剪继续画。
            // 擦干净之后（再下一帧起）uniform 会一直保持 0，于是可以安心早退。
            //
            // 收益：不开镜时，每个 draw call 的开销从「5 次反射 + 2 次 GL 查询」
            // 降到两次布尔读取。这条路径与开不开镜无关地跑在<b>每一帧</b>上，
            // 所以这就是「没开镜时帧数也差」的那一份。
            if (!ScopeMaskRenderer.hasMaskThisFrame() && !ScopeMaskRenderer.hadMaskLastFrame()) {
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

            // 驱动常量，问一次记住 —— 原来这一句也在逐 draw call 做 GL 查询。
            if (cachedMaxTextureUnits < 0) {
                cachedMaxTextureUnits = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
            }
            int unit = Math.max(15, cachedMaxTextureUnits - 1);
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

    /**
     * {@code GlRenderPass.pipeline}，字段对象按 class 缓存一次。
     *
     * <p>运行期这个 class 实际上恒定，所以「上次是哪个 class」比一下就够，
     * 不必上 map。见 {@link #cachedPipelineField} 的注释。
     */
    private static Field pipelineField(Object glRenderPass) {
        Class<?> cls = glRenderPass.getClass();
        if (cls != cachedPassClass || !pipelineFieldResolved) {
            cachedPassClass = cls;
            cachedPipelineField = null;
            for (Class<?> c = cls; c != null && cachedPipelineField == null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("pipeline");
                    f.setAccessible(true);
                    cachedPipelineField = f;
                } catch (NoSuchFieldException ignored) {
                    // 继续往父类找
                }
            }
            pipelineFieldResolved = true;
        }
        return cachedPipelineField;
    }

    private static int resolveMode(Object glRenderPass) {
        try {
            if (glRenderPass == null) {
                return 0;
            }
            Field pipelineField = pipelineField(glRenderPass);
            if (pipelineField == null) {
                return 0;
            }
            Object glPipeline = pipelineField.get(glRenderPass);
            if (glPipeline == null) {
                return 0;
            }
            // 同一个管线实例的判定结果恒定，记住即可 —— 省掉后面那四次反射
            // 与一次 toLowerCase 分配。
            Integer remembered = MODE_BY_PIPELINE.get(glPipeline);
            if (remembered != null) {
                return remembered;
            }
            int resolved = resolveModeUncached(glPipeline);
            if (MODE_BY_PIPELINE.size() >= MODE_CACHE_LIMIT) {
                MODE_BY_PIPELINE.clear();
            }
            MODE_BY_PIPELINE.put(glPipeline, resolved);
            return resolved;
        } catch (Throwable t) {
            logOnce("resolve scope render pass", t);
        }
        return 0;
    }

    /** 真正去问「这套管线是不是我们的镜身/准星管线」。只在每个管线实例上跑一次。 */
    private static int resolveModeUncached(Object glPipeline) {
        try {
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
                // 【恒为 1】镜身在孔径内 discard，于是最终画面里孔径那块就是 1× 的世界。
                //
                // 镜内的「放大」不在这里做 —— 那是
                // {@code ScopePipRenderer.compositeAfterLevelUnderShaders()} 的活：
                // 等 Iris 整条管线跑完，直接在最终画面上把孔径内那 1/Z 的小块放大铺满。
                // 而「孔径内是干净的 1× 世界、没有枪」正是这里 discard 换来的前提。
                //
                // ↓ 以下是被推翻的旧方案，留作路标，别再走一遍 ↓
                // 曾经让这里返回 3，由注入进 pack 着色器的分支去采样 colortexN。
                // 两次实测都失败：先是纯黑（HAND_CUTOUT 跑在延迟光照之前，
                // 那一刻没有任何 colortex 装着已着色的场景），把枪挪进半透明 pass 之后
                // 又变成「灰噪块 + 黑」（场景色逐 pack 不同，Eclipse 在 colortex2）。
                // 根子上这条路要求猜中别家 pack 的内部约定，怎么修都是下一次盲猜。
                return 1;
            }
            if (FLASH_TRANSLUCENT_PIPELINE.equals(normalized)
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
