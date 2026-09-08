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
import net.minecraft.world.InteractionHand;
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
     * The post-composite overlay hook is bytecode-audited specifically against Iris 1.10.7 for
     * Minecraft 1.21.11. Other Iris lines retain the HAND_TRANSLUCENT fallback instead of risking
     * an invisible reticle when internal final-render timing changes.
     */
    public static boolean supportsFinalScopeOverlay() {
        return FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS)
                .map(container -> container.getMetadata().getVersion().getFriendlyString().startsWith("1.10.7"))
                .orElse(false);
    }

    /**
     * Whether the active Iris line exposes the hand-flush timing the mesh GPU path relies on.
     *
     * <p>The mesh GPU pass under a shader pack is drawn from {@code ItemInHandRenderer#renderHandsWithItems}
     * immediately after the flush that Iris replaces there ({@code HandRenderer#endRender()} calls
     * {@code FeatureRenderDispatcher#renderAllFeatures()} + {@code BufferSource#endBatch()}). That
     * replacement pair is what was audited for Iris 1.10.x on 1.21.11; other Iris lines keep the
     * mesh guns on the collector path. A stale assumption here would only cost a frame (the
     * submit-side liveness proof in {@code PolyMeshGpuRenderer} falls back to the collector), but
     * an audited version gate is the cheaper guarantee.</p>
     */
    public static boolean supportsHandFlushHook() {
        return FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS)
                .map(container -> container.getMetadata().getVersion().getFriendlyString().startsWith("1.10."))
                .orElse(false);
    }

    /**
     * Classify the mesh renderer's own pipeline as Iris' hand program so the resident-VBO pass,
     * which never goes through a vanilla {@code RenderType}, still receives shader-pack lighting.
     *
     * <p>{@code IrisApi.assignPipeline} maps a {@link RenderPipeline} to an Iris program; Iris'
     * {@code ShaderKey.findBestMatch} picks {@code HAND_CUTOUT} for our pipeline because it declares
     * {@code ALPHA_CUTOUT} and the (possibly Iris-extended) entity vertex format. Failures are
     * swallowed the same way as the scope pipelines: without the assignment the gun still draws,
     * just with vanilla lighting.</p>
     */
    public static boolean assignMeshPipelineToHand(RenderPipeline pipeline) {
        return assignPipelineToIris(pipeline, "HAND", "mesh_entity_hand");
    }

    /**
     * Same classification for the <b>world</b> mesh pass: the resident-VBO pipeline should be lit
     * by the pack's entity program instead of falling back to the vanilla one.
     *
     * <p>The constant is {@code IrisProgram.ENTITIES}; the full enum of the Iris 1.10.7 jar on the
     * 1.21.11 classpath was dumped by {@code dumpHandFlushApi}, and it exposes {@code BASIC},
     * {@code TERRAIN*}, {@code ENTITIES}, {@code ENTITIES_TRANSLUCENT}, {@code EMISSIVE_ENTITIES},
     * {@code HAND}, {@code HAND_TRANSLUCENT}, {@code PARTICLES*}, {@code BLOCK*}, {@code CLOUDS},
     * {@code SKY_*}, {@code ARMOR_GLINT}, {@code BEACON_BEAM}, {@code LINES}, {@code TEXTURED},
     * {@code TRANSLUCENT} -- there is no {@code ENTITY}/{@code MAIN}, so an earlier guess would
     * simply have logged one warning and left the gun unlit. {@code EMISSIVE_ENTITIES} is
     * deliberately <b>not</b> used for this renderer's unlit fallback pipeline: that pipeline only
     * skips the lightmap texture, it does not mean "always full bright".</p>
     *
     * <p>{@code MeshGpuWorldUnderShaders} still defaults to false: the constant is now known, but
     * the combination has never been run in-game.</p>
     */
    public static boolean assignMeshPipelineToEntity(RenderPipeline pipeline) {
        return assignPipelineToIrisAny(pipeline, new String[]{"ENTITIES"}, "mesh_entity_world");
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
            boolean itemTranslucent = isMainHandTranslucent(handRendererClass, instance, stack);
            return renderingSolid != itemTranslucent;
        } catch (Throwable ignored) {
            // Fail open: a broken optional Iris reflection bridge must not make the held item vanish.
            return true;
        }
    }

    /**
     * Iris 1.10.7 classifies a hand by {@link InteractionHand}, not by {@link ItemStack}. The
     * old ItemStack-only reflection lookup always failed on 1.21.11 and consequently failed open,
     * causing TACZ to re-submit the full opaque gun during a translucent hand pass. Keep the old
     * overload as a compatibility fallback for other Iris lines.
     */
    private static boolean isMainHandTranslucent(Class<?> handRendererClass,
                                                  Object instance,
                                                  ItemStack stack) throws ReflectiveOperationException {
        try {
            return (Boolean) handRendererClass
                    .getMethod("isHandTranslucent", InteractionHand.class)
                    .invoke(instance, InteractionHand.MAIN_HAND);
        } catch (NoSuchMethodException ignored) {
            return (Boolean) handRendererClass
                    .getMethod("isHandTranslucent", ItemStack.class)
                    .invoke(instance, stack);
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
