package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.legacy.IrisCompatLegacy;
import com.tacz.guns.compat.iris.newly.IrisCompatNewly;
import com.tacz.guns.init.CompatRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Optional Iris integration for the Minecraft 26.1.2 OpenGL renderer. */
public final class IrisCompat {
    private static final Version SHADOW_API_SPLIT_VERSION;

    static {
        try {
            SHADOW_API_SPLIT_VERSION = Version.parse("1.7.0");
        } catch (VersionParsingException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Supplier<Boolean> isRenderingShadow = () -> false;
    private static final Set<RenderPipeline> ASSIGNED_SCOPE_PIPELINES = new HashSet<>();
    private static boolean loggedScopePipelineFailure;

    private IrisCompat() {
    }

    public static void initCompat() {
        FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS).ifPresent(mod -> {
            if (mod.getMetadata().getVersion().compareTo(SHADOW_API_SPLIT_VERSION) >= 0) {
                isRenderingShadow = IrisCompatNewly::isRenderShadow;
            } else {
                isRenderingShadow = IrisCompatLegacy::isRenderShadow;
            }
        });
    }

    public static boolean isRenderShadow() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return false;
        }
        try {
            return isRenderingShadow.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isUsingRenderPack() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (Boolean) apiClass.getMethod("isShaderPackInUse").invoke(api);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Classifies a TACZ custom pipeline through Iris' public API while keeping Iris optional. */
    public static synchronized boolean assignPipelineToIris(RenderPipeline pipeline,
                                                            String irisProgramName,
                                                            String debugName) {
        return assignPipelineToIrisAny(pipeline, new String[]{irisProgramName}, debugName);
    }

    private static boolean assignPipelineToIrisAny(RenderPipeline pipeline, String[] irisProgramNames, String debugName) {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return false;
        }
        if (ASSIGNED_SCOPE_PIPELINES.contains(pipeline)) {
            return true;
        }

        Throwable lastFailure = null;
        for (String irisProgramName : irisProgramNames) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object irisProgram = Enum.valueOf(
                        (Class<? extends Enum>) programClass.asSubclass(Enum.class), irisProgramName);
                apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass)
                        .invoke(api, pipeline, irisProgram);
                ASSIGNED_SCOPE_PIPELINES.add(pipeline);
                GunMod.LOGGER.info("[TACZ Iris] Assigned {} to the Iris {} program.",
                        debugName, irisProgramName);
                return true;
            } catch (Throwable t) {
                if (isAlreadyAssigned(t)) {
                    // Iris' coreShaderMap already routes this pipeline (either from a previous call
                    // of ours after a resource reload, or from IrisPipelines' own defaults). That is
                    // the desired end state, so treat it as success instead of logging a stack trace.
                    ASSIGNED_SCOPE_PIPELINES.add(pipeline);
                    return true;
                }
                lastFailure = t;
            }
        }

        if (!loggedScopePipelineFailure) {
            loggedScopePipelineFailure = true;
            GunMod.LOGGER.warn("[TACZ Iris] Iris cannot classify render pipeline {} as {}; "
                            + "vanilla pipeline behavior will be used.",
                    debugName, String.join("/", irisProgramNames), lastFailure);
        }
        return false;
    }

    /** @return whether {@code t} (or a cause of it) is Iris' "Shader already assigned" rejection. */
    private static boolean isAlreadyAssigned(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof IllegalStateException) {
                String message = current.getMessage();
                if (message != null && message.contains("already assigned")) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /**
     * No-op kept only so older call sites keep compiling.
     *
     * <p><b>为什么删掉原来的实现（这是一条真实的渲染 BUG，不是清理）。</b>旧实现会用
     * {@code IrisApi#assignPipeline} 把 vanilla 的 {@code ENTITY_CUTOUT} /
     * {@code ENTITY_CUTOUT_CULL} / {@code ENTITY_TRANSLUCENT*} / {@code ITEM_CUTOUT} /
     * {@code ITEM_TRANSLUCENT} <b>常量地</b>重定向到 Iris 的 {@code HAND_CUTOUT} /
     * {@code HAND_TRANSLUCENT} program。
     *
     * <p>但 Iris 的 {@code IrisPipelines} 静态初始化里，这些管线本来就已经登记为
     * <b>动态</b>函数：{@code ENTITY_CUTOUT -> getCutout(p)}，其内部按
     * {@code HandRenderer.INSTANCE.isActive()} 在
     * {@code HAND_CUTOUT_DIFFUSE} / {@code BLOCK_ENTITY_DIFFUSE} / {@code ENTITIES_CUTOUT_DIFFUSE}
     * 之间选择。也就是说「第一人称手部 pass 下走手部 program」这件事 Iris 自己已经做对了，
     * 我们无事可做。
     *
     * <p>而一旦这个覆盖<b>成功</b>（换个 Iris 版本、换个初始化顺序、或 Iris 未来把
     * {@code coreShaderMap} 改成允许覆盖），后果是<b>整个世界里所有实体与掉落物都会用
     * shader pack 的 {@code gbuffers_hand} 绘制</b>，并被送进手部的 before/after-translucent
     * FBO —— 这正是「光影下画面整个不对，而且每台机器不一样」的量级。
     * 在 26.1.2 + Iris 1.11.2 上它只是抛
     * {@code IllegalStateException: Shader already assigned: minecraft:pipeline/entity_cutout: HAND_CUTOUT}
     * 并向日志倾倒一整段 {@code InvocationTargetException} 堆栈（远端 latest.log 20:32:34 即此），
     * 属于「侥幸没生效」。因此彻底移除，而不是「捕获异常当作成功」。
     *
     * @deprecated Iris already routes the vanilla entity/item pipelines to its hand programs while
     * {@code HandRenderer} is active; overriding that mapping is never correct.
     */
    @Deprecated(forRemoval = true)
    public static synchronized void assignCommonEntityPipelinesToHandIfNeeded() {
        // Intentionally empty. See the javadoc above.
    }

    /**
     * Iris renders hands from its own solid/translucent level phases and suppresses vanilla's hand call.
     * This flag is also used by TACZ's view-bob handling.
     */
    public static boolean isHandRendererActive() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS) || !isUsingRenderPack()) {
            return false;
        }
        try {
            Class<?> handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
            Object instance = handRendererClass.getField("INSTANCE").get(null);
            return (Boolean) handRendererClass.getMethod("isActive").invoke(instance);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** @deprecated Feature rendering owns batch flushes in 26.1.2. */
    @Deprecated
    public static boolean endBatch(Object bufferSource) {
        return false;
    }

    /** Feature rendering owns batch flushes in 26.1.2. */
    public static boolean endBatch(SubmitNodeCollector collector) {
        return false;
    }
}
