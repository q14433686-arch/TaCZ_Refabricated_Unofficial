package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
 */
public class MeshyConfig {

    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;
    public static ForgeConfigSpec.BooleanValue LOG_STATS;
    public static ForgeConfigSpec.BooleanValue GPU_BAKING;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("mesh_loader");

        builder.comment("Master switch for TacZ Mesh Loader poly_mesh rendering. ",
                "Cube-only rendering is unaffected.");
        ENABLE_MESH = builder.define("MeshEnable", true);

        builder.comment("Whether to render poly_mesh during shadow passes. ",
                "Default false: the cube body already provides shadow shapes.");
        POLY_IN_SHADOW = builder.define("MeshPolyInShadow", false);

        builder.comment("Maximum distance (blocks) to render poly_mesh in world contexts ",
                "(other players, dropped items, blocks). 0 = unlimited. ",
                "First-person view is always rendered in full.");
        MAX_RENDER_DISTANCE = builder.defineInRange("MeshMaxRenderDistance", 48.0, 0.0, 1_000_000.0);

        builder.comment("Whether to render poly_mesh in GUI/FIXED preview contexts ",
                "(e.g. gun workbench preview).");
        POLY_IN_PREVIEW = builder.define("MeshPolyInPreview", true);

        builder.comment("Log poly_mesh statistics (bone/vertex counts) when models load.");
        LOG_STATS = builder.define("MeshLogStats", true);

        builder.comment("GPU static baking: each bone's vertices are uploaded once and only ",
                "per-bone transforms are updated every frame. First-person high-poly guns ",
                "drop from O(vertices) to O(bones) CPU work. Automatically falls back to ",
                "the CPU path when an Iris shader pack is active, or if the GPU pass fails.");
        GPU_BAKING = builder.define("MeshGpuBaking", true);

        builder.pop();
    }

    private MeshyConfig() {
    }
}
