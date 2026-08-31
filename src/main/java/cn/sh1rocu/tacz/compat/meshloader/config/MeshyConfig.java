package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
 *
 * <h2>范围</h2>
 * <p>collector（VertexConsumer）渲染路径 + 解析缓存 + 顶点预算闸门，
 * 外加第一人称 GPU 静态烘焙（{@code MeshGpuBaking}，见
 * {@code PolyMeshGpuRenderer} —— 只收手部 pass，规避关 PR
 * #33/#69/#70/#71 的世界 pass 泄漏）。光影下默认回退 collector；
 * {@code MeshGpuUnderShaders} 是实验性开关（第 2 步 v2：把 pass 开在
 * Iris 自己那次手部 flush 之内，见 {@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md}）。</p>
 */
public final class MeshyConfig {

    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;
    public static ForgeConfigSpec.BooleanValue LOG_STATS;
    public static ForgeConfigSpec.BooleanValue GPU_BAKING;
    public static ForgeConfigSpec.BooleanValue GPU_UNDER_SHADERS;
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

        builder.comment("EXPERIMENTAL: keep the GPU-baked mesh gun on the resident-VBO path when a",
                "shader pack is active. The pass is then opened inside Iris' own hand flush, so it",
                "lands in the gbuffer and is lit by the pack's gbuffers_hand program (the pipeline is",
                "registered with IrisApi.assignPipeline(HAND)); requires an audited Iris 1.10.x and",
                "silently falls back to the collector path if the flush hook is not live.",
                "Off by default: the collector path already gets correct shader lighting, this only",
                "removes the per-frame CPU vertex transform. See",
                "docs/TML_GPU_STEP2_HANDFLUSH_20260831.md.");
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

        builder.pop();
    }

    private MeshyConfig() {
    }
}
