package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
 *
 * <h2>范围</h2>
 * <p>collector（VertexConsumer）渲染路径 + 解析缓存 + 顶点预算闸门，
 * 外加第一人称 GPU 静态烘焙（{@code MeshGpuBaking}，见
 * {@code PolyMeshGpuRenderer} —— 只收手部 pass，规避关 PR
 * #33/#69/#70/#71 的世界 pass 泄漏）。光影下默认回退 collector，
 * {@code MeshGpuUnderShaders} 是实验性强开。</p>
 */
public final class MeshyConfig {

    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;
    public static ForgeConfigSpec.BooleanValue LOG_STATS;
    public static ForgeConfigSpec.BooleanValue GPU_BAKING;
    public static ForgeConfigSpec.BooleanValue GPU_WORLD;
    public static ForgeConfigSpec.IntValue GPU_LIGHT_CACHE_SIZE;
    public static ForgeConfigSpec.IntValue GPU_BAKE_BUDGET_PER_FRAME;
    public static ForgeConfigSpec.BooleanValue GPU_UNDER_SHADERS;
    public static ForgeConfigSpec.BooleanValue POLY_MIRROR_REVERSE_WINDING;
    public static ForgeConfigSpec.BooleanValue POLY_INVERT_NORMALS;
    public static ForgeConfigSpec.BooleanValue POLY_PREFER_PACK_NORMALS;
    public static ForgeConfigSpec.BooleanValue POLY_ILLUMINATED_REAL_SKY;
    public static ForgeConfigSpec.IntValue GUI_MAX_VERTICES;
    public static ForgeConfigSpec.IntValue WORLD_MAX_VERTICES;
    public static ForgeConfigSpec.DoubleValue WORLD_FULL_DETAIL_DISTANCE;
    public static ForgeConfigSpec.IntValue MAX_MODEL_VERTICES;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("mesh_loader");

        builder.comment("Master switch for TacZ Mesh Loader poly_mesh rendering.",
                "Cube-only rendering is unaffected.");
        ENABLE_MESH = builder.define("MeshEnable", true);

        builder.comment("Whether to render poly_mesh during shadow passes.",
                "Default false: the cube body already provides shadow shapes,",
                "and skipping the shadow pass halves the per-frame vertex cost",
                "for high-poly guns under shader packs.");
        POLY_IN_SHADOW = builder.define("MeshPolyInShadow", false);

        builder.comment("Maximum distance (blocks) to render poly_mesh in world contexts",
                "(other players, dropped items). 0 = unlimited.",
                "First-person view is always rendered in full.");
        MAX_RENDER_DISTANCE = builder.defineInRange("MeshMaxRenderDistance", 48.0, 0.0, 1_000_000.0);

        builder.comment("Whether to render poly_mesh in GUI/FIXED preview contexts.");
        POLY_IN_PREVIEW = builder.define("MeshPolyInPreview", true);

        builder.comment("Log poly_mesh statistics (bone/vertex counts) when models load.");
        LOG_STATS = builder.define("MeshLogStats", true);

        builder.comment("GPU static baking for FIRST-PERSON only: vertices stay in bone-local",
                "space in a resident VBO; each frame uploads O(bones) matrices instead of",
                "transforming every vertex on the CPU.",
                "World/GUI/drops stay on the collector path so they cannot leak into",
                "the world pass (that was the closed PRs' wrong-screenshot bug).",
                "Falls back to the collector path if the GPU pass fails.");
        GPU_BAKING = builder.define("MeshGpuBaking", true);

        builder.comment("GPU static baking for WORLD contexts too: third-person guns held by",
                "other players, dropped items, item frames and display statues draw from",
                "the same resident VBOs, uploading O(bones) matrices per gun per frame",
                "instead of transforming every vertex on the CPU. This is what makes",
                "multiplayer with many high-poly mesh guns playable. Light is handled by",
                "a small per-light-level VBO cache (see MeshGpuLightCacheSize).",
                "Under shader packs world guns draw via the vanilla RenderType route",
                "(same mechanism as first-person). Requires MeshGpuBaking.",
                "Falls back to the collector path if the GPU pass fails.");
        GPU_WORLD = builder.define("MeshGpuWorld", true);

        builder.comment("How many quantized light levels of baked world VBOs to keep per gun",
                "model (LRU). Upstream TML uses 8 unquantized levels; we quantize light",
                "to a coarse grid first, so even 4 covers nearly all scenes. Each cached",
                "level costs GPU memory proportional to the model's vertex count.",
                "First-person baking is unaffected (it keeps a single level).");
        GPU_LIGHT_CACHE_SIZE = builder.defineInRange("MeshGpuLightCacheSize", 4, 1, 16);

        builder.comment("How many world bakes may run in a single frame. Prevents evict-rebake",
                "thrash when a scene spans more light levels than the cache holds - guns",
                "beyond the budget fall back to the CPU path for that frame. Independent",
                "of MeshGpuLightCacheSize (cache size = GPU memory; budget = per-frame",
                "CPU/upload cost).");
        GPU_BAKE_BUDGET_PER_FRAME = builder.defineInRange("MeshGpuBakeBudgetPerFrame", 4, 1, 64);

        builder.comment("Shader packs: force the RAW GPU pass (custom pipeline) instead of the",
                "default vanilla-RenderType route. The default route feeds the resident VBOs",
                "through RenderType.prepare()/drawFromBuffer with the ENTITY_CUTOUT pipeline,",
                "which Iris intercepts into its HAND program - gun body gets shader lighting.",
                "true = bypass the shader pipeline entirely (NO shader lighting on the gun;",
                "diagnostic fallback in case the default route misbehaves with some pack).");
        GPU_UNDER_SHADERS = builder.define("MeshGpuUnderShaders", false);

        builder.comment("Vertex budget for poly_mesh in GUI/FIXED/HEAD. Icons above this",
                "budget render cube-only (or the pack's LOD model when present).",
                "0 = unlimited.");
        GUI_MAX_VERTICES = builder.defineInRange("MeshGuiMaxVertices", 65536, 0, 10_000_000);

        builder.comment("Vertex budget for poly_mesh in third-person / dropped-item / frame",
                "contexts. Above this budget only cubes are drawn. 0 = unlimited.",
                "Within MeshWorldFullDetailDistance the budget is waived entirely.");
        WORLD_MAX_VERTICES = builder.defineInRange("MeshWorldMaxVertices", 120000, 0, 10_000_000);

        builder.comment("Within this distance (blocks), in-world poly_mesh (third-person,",
                "dropped items, item frames, display statues) always renders in full detail,",
                "ignoring the vertex budgets above. High-poly guns without a pack-provided",
                "LOD model would otherwise vanish to cube-only right in front of the player.",
                "Beyond this distance the budgets apply as usual. 0 = no exemption.");
        WORLD_FULL_DETAIL_DISTANCE = builder.defineInRange("MeshWorldFullDetailDistance", 16.0, 0.0, 1024.0);

        builder.comment("Soft warning threshold logged once per geo at load time.",
                "Does not change rendering; tells pack authors the model is too dense.");
        MAX_MODEL_VERTICES = builder.defineInRange("MeshMaxModelVertices", 120000, 0, 10_000_000);

        builder.comment("poly_mesh positions are Y-mirrored at load; a single-axis mirror flips",
                "every face's front/back. Upstream TML never reverses the emit winding to",
                "match, so baked outward normals contradict gl_FrontFacing - invisible in",
                "vanilla (no culling, entity shader ignores normals) but shader packs that",
                "do 'normal *= gl_FrontFacing ? 1 : -1' put highlights on the wrong side.",
                "true = reverse winding on mirrored meshes (matches how TACZ's own",
                "BedrockPolygon handles mirror). Requires resource reload to take effect.");
        POLY_MIRROR_REVERSE_WINDING = builder.define("MeshPolyMirrorReverseWinding", true);

        builder.comment("Diagnostic: flip all baked poly_mesh normals. Only for testing packs",
                "whose meshes were authored inside-out. Requires resource reload.");
        POLY_INVERT_NORMALS = builder.define("MeshPolyInvertNormals", false);

        builder.comment("Use the gun pack's own 'normals' arrays (smooth shading) instead of",
                "flat face normals. Upstream TML always forced flat shading, so curved",
                "surfaces show faceted highlights under shader packs. Off by default",
                "because most packs were authored against flat shading.",
                "Requires resource reload.");
        POLY_PREFER_PACK_NORMALS = builder.define("MeshPolyPreferPackNormals", false);

        builder.comment("'_illuminated' bones are forced to full brightness 0xF000F0 - both the",
                "block AND the sky light column maxed. Vanilla needs both (the lightmap",
                "multiplies the two columns), but shader packs read sky=15 as 'this surface",
                "can see the sky', so glowing sights inherit sun/moon lighting at night.",
                "true = when a shader pack is active, keep block=15 but use the real",
                "environment sky light for poly_mesh illuminated bones. Cube-layer",
                "illuminated bones are unaffected (that convention belongs to TACZ core).");
        POLY_ILLUMINATED_REAL_SKY = builder.define("MeshPolyIlluminatedRealSky", true);

        builder.pop();
    }

    private MeshyConfig() {
    }
}
