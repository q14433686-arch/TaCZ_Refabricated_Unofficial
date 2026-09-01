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

    /**
     * poly_mesh GPU 路径（常驻 VBO）在手部 flush 的绘制钩子是否可用。
     *
     * <p>该钩子依赖 Iris 26.1 线的 {@code MixinItemInHandRenderer} 拦截架构（源码核实，
     * Iris 26.1 分支 commit f4c0697）：{@code @WrapWithCondition} 掏掉
     * {@code renderHandsWithItems} 里的 {@code renderAllFeatures()}，
     * {@code @WrapOperation} 把 {@code endBatch()} 换成 {@code HandRenderer#endRender()}
     * （内部仍是 renderAllFeatures + endBatch），并且 Iris 自己也是从
     * {@code iris$renderHandsWithCustomRenderer} → <b>同一个</b> {@code renderHandsWithItems}
     * 进来的 —— 所以 TACZ 的 {@code @Inject(renderHandsWithItems, RETURN)} 钩子天然落在
     * Iris 的手部阶段内。这一架构在本仓审计基线（Iris 1.11.x + MC 26.1.2，
     * {@code supportsFinalScopeOverlay} 同款版本门）上成立。</p>
     *
     * <p>版本不匹配时返回 false：{@code MeshGpuUnderShaders} 的路径整体拒收并保持
     * collector（宁可不加速，不能画错）。</p>
     */
    public static boolean supportsHandFlushHook() {
        return FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS)
                .map(container -> container.getMetadata().getVersion().getFriendlyString().startsWith("1.11"))
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
     * <p>常量已按 Q4 要求核实（Iris 26.1 分支源码 {@code api/v0/IrisProgram.java}）：
     * 全量枚举为 {@code BASIC, TEXTURED, TERRAIN, TERRAIN_SOLID, TERRAIN_CUTOUT, TRANSLUCENT,
     * SKY_BASIC, SKY_TEXTURED, ARMOR_GLINT, ENTITIES, ENTITIES_TRANSLUCENT, CLOUDS, BLOCK,
     * BLOCK_TRANSLUCENT, HAND, HAND_TRANSLUCENT, PARTICLES, PARTICLES_TRANSLUCENT,
     * EMISSIVE_ENTITIES, BEACON_BEAM, LINES} —— 没有 {@code ENTITY}/{@code MAIN}，
     * 世界路径用 {@code ENTITIES}。{@code EMISSIVE_ENTITIES} 刻意<b>不</b>用于本渲染器的
     * 无光照兜底管线：那条只跳过 lightmap 采样，不等于「恒全亮」。</p>
     *
     * <p>{@code MeshGpuWorldUnderShaders} 保持默认 false：组合已按源码核实，但 26.1.2 上
     * 没有实机验证（见 MESH_LOADER.md 复测矩阵）。</p>
     */
    public static boolean assignMeshPipelineToEntity(RenderPipeline pipeline) {
        return assignPipelineToIrisAny(pipeline, new String[]{"ENTITIES"}, "mesh_entity_world");
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
