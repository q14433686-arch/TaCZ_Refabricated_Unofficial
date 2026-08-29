package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
 *
 * <h2>范围（安全子集）</h2>
 * <p>本轮内置只包含 collector（VertexConsumer）渲染路径 + 解析缓存 +
 * 顶点预算闸门；<b>没有</b> GPU 静态烘焙 —— 关闭的 PR #33/#69/#70/#71/#72
 * 里 GPU 路径四次翻车（世界 pass 泄漏 / 深度声明 / 光影回退语义），
 * 按 docs/TML_PERF_DIRECTIONS_2026_08_29.md 的顺序，GPU 路径等
 * 无光影 PoC 通过后单独提交。因此这里没有 MeshGpuBaking 等键，
 * 避免出现「注册了但没人读」的配置陷阱（本仓有 HandViewLockFix 案底）。
 */
public final class MeshyConfig {

    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;
    public static ForgeConfigSpec.BooleanValue LOG_STATS;
    public static ForgeConfigSpec.IntValue GUI_MAX_VERTICES;
    public static ForgeConfigSpec.IntValue WORLD_MAX_VERTICES;
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

        builder.comment("Vertex budget for poly_mesh in GUI/FIXED/HEAD. Icons above this",
                "budget render cube-only (or the pack's LOD model when present).",
                "0 = unlimited.");
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
