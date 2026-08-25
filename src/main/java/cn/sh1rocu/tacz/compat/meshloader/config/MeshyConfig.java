package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
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
    public static ForgeConfigSpec.IntValue MAX_MODEL_VERTICES;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("mesh_loader");

        builder.comment("Master switch for TacZ Mesh Loader poly_mesh rendering.",
                "Cube-only rendering is unaffected.");
        ENABLE_MESH = builder.define("MeshEnable", true);

        builder.comment("Whether to render poly_mesh during shadow passes.",
                "Default false: the cube body already provides shadow shapes.");
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
                "space in a resident VBO; each frame uploads O(bones) matrices.",
                "World/GUI/drops stay on the collector path so they cannot leak into",
                "the world pass (that was why closed PRs had wrong screenshots).",
                "Falls back to the collector path if the GPU pass fails.");
        GPU_BAKING = builder.define("MeshGpuBaking", true);

        builder.comment("EXPERIMENTAL: keep the first-person GPU mesh pass while an Iris",
                "shader pack is enabled. The custom pass bypasses the shader pipeline,",
                "so the gun body will NOT receive shader-pack lighting.",
                "Default false: shader packs use the collector bulk path.");
        GPU_UNDER_SHADERS = builder.define("MeshGpuUnderShaders", false);

        builder.comment("Vertex budget for poly_mesh in GUI/FIXED/HEAD. Icons above this",
                "budget render cube-only. 0 = unlimited.");
        GUI_MAX_VERTICES = builder.defineInRange("MeshGuiMaxVertices", 65536, 0, 10_000_000);

        builder.comment("Vertex budget for poly_mesh in third-person / dropped-item / frame",
                "contexts. Above this budget only cubes are drawn. 0 = unlimited.");
        WORLD_MAX_VERTICES = builder.defineInRange("MeshWorldMaxVertices", 120000, 0, 10_000_000);

        builder.comment("Soft warning threshold logged once per geo at load time.",
                "Does not change rendering; tells pack authors the model is too dense.");
        MAX_MODEL_VERTICES = builder.defineInRange("MeshMaxModelVertices", 120000, 0, 10_000_000);

        builder.pop();
    }

    private MeshyConfig() {
    }
}
