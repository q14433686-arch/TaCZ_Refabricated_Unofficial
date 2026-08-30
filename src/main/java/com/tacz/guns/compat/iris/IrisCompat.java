package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.legacy.IrisCompatLegacy;
import com.tacz.guns.compat.iris.newly.IrisCompatNewly;
import com.tacz.guns.init.CompatRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;

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
    private static boolean commonEntityPipelinesAssigned = false;
    private static boolean commonEntityPipelinesAssignAttempted = false;

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
                    ASSIGNED_SCOPE_PIPELINES.add(pipeline);
                    GunMod.LOGGER.debug("[TACZ Iris] {} is already classified by Iris; keeping existing assignment.",
                            debugName);
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

    private static boolean isAlreadyAssigned(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("Shader already assigned")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Assign vanilla entity/item pipelines used inside the first-person hand pass to Iris' hand
     * programs. Some Iris versions otherwise rediscover the same "perfect program match" every
     * frame. Try this once per client session only, even if a subset fails.
     */
    public static synchronized void assignCommonEntityPipelinesToHandIfNeeded() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return;
        }
        if (commonEntityPipelinesAssigned || commonEntityPipelinesAssignAttempted) {
            return;
        }
        commonEntityPipelinesAssignAttempted = true;

        boolean ok = true;
        ok &= assignPipelineToIrisAny(RenderPipelines.ENTITY_CUTOUT,
                new String[]{"HAND_CUTOUT", "HAND"}, "entity_cutout");
        ok &= assignPipelineToIrisAny(RenderPipelines.ENTITY_CUTOUT_CULL,
                new String[]{"HAND_CUTOUT", "HAND"}, "entity_cutout_cull");
        ok &= assignPipelineToIrisAny(RenderPipelines.ENTITY_TRANSLUCENT,
                new String[]{"HAND_TRANSLUCENT"}, "entity_translucent");
        ok &= assignPipelineToIrisAny(RenderPipelines.ENTITY_TRANSLUCENT_CULL,
                new String[]{"HAND_TRANSLUCENT"}, "entity_translucent_cull");
        ok &= assignPipelineToIrisAny(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,
                new String[]{"HAND_TRANSLUCENT"}, "entity_translucent_emissive");
        ok &= assignPipelineToIrisAny(RenderPipelines.ITEM_CUTOUT,
                new String[]{"HAND_CUTOUT", "HAND"}, "item_cutout");
        ok &= assignPipelineToIrisAny(RenderPipelines.ITEM_TRANSLUCENT,
                new String[]{"HAND_TRANSLUCENT"}, "item_translucent");

        commonEntityPipelinesAssigned = ok;
    }

    /**
     * The post-composite overlay hook ({@code IrisRenderingPipeline#finalizeLevelRendering} TAIL)
     * and the late translucent hand pass are bytecode-audited specifically against the Iris
     * <b>26.1 分支</b>（1.11.x，本分支审计基线 commit
     * f4c06978f3a1c64869e40cd5cc7c8ed383085cc0，对应 MC 26.1.2）。其他 Iris 构建保持原 solid-pass
     * 行为，而不是冒险在内部 final 时序变化时得到一颗隐形准星。
     */
    public static boolean supportsFinalScopeOverlay() {
        return FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS)
                .map(container -> container.getMetadata().getVersion().getFriendlyString().startsWith("1.11"))
                .orElse(false);
    }

    /**
     * @return whether the active Iris hand renderer is currently extracting its solid pass.
     *         A scope reticle is frozen only in this pass and emitted later by the Iris-only
     *         {@code HAND_TRANSLUCENT} bridge.
     */
    public static boolean isRenderingSolidHandPass() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS) || !isUsingRenderPack()) {
            return false;
        }
        try {
            Class<?> handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
            Object instance = handRendererClass.getField("INSTANCE").get(null);
            return (Boolean) handRendererClass.getMethod("isActive").invoke(instance)
                    && (Boolean) handRendererClass.getMethod("isRenderingSolid").invoke(instance);
        } catch (Throwable ignored) {
            return false;
        }
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

    /**
     * Mirrors Iris' own {@code MixinItemInHandRenderer#iris$skipTranslucentHands} phase gate for
     * TACZ' cancellable first-person renderer.
     *
     * <p>Iris renders first-person hands twice when either held item is considered translucent:
     * once during {@code HAND_SOLID} and once during {@code HAND_TRANSLUCENT}. Vanilla item/arm
     * rendering is protected by Iris' HEAD injection in {@code renderArmWithItem}; TACZ replaces
     * that method at the same injection point, so depending on mixin callback order our custom
     * gun renderer can bypass Iris' guard and submit an opaque gun/arm batch again in the
     * translucent pass. Shader packs then composite the duplicated hand buffer as translucent,
     * which looks exactly like missing/see-through gun shells and arms while shaders are enabled.
     *
     * <p>When Iris is not actively rendering a shader-pack hand pass this returns {@code true},
     * preserving vanilla/no-shader behavior. During an Iris hand pass it applies the same boolean
     * as Iris: solid items render only in the solid phase; translucent block items render only in
     * the translucent phase.</p>
     */
    public static boolean shouldRenderInCurrentHandPhase(ItemStack stack) {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS) || !isUsingRenderPack()) {
            return true;
        }
        try {
            Class<?> handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
            Object instance = handRendererClass.getField("INSTANCE").get(null);
            boolean active = (Boolean) handRendererClass.getMethod("isActive").invoke(instance);
            if (!active) {
                return true;
            }
            boolean renderingSolid = (Boolean) handRendererClass.getMethod("isRenderingSolid").invoke(instance);
            boolean itemTranslucent = (Boolean) handRendererClass.getMethod("isHandTranslucent", ItemStack.class)
                    .invoke(instance, stack);
            return renderingSolid != itemTranslucent;
        } catch (Throwable ignored) {
            // Fail open: a broken optional Iris reflection bridge must not make the held item vanish.
            return true;
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
