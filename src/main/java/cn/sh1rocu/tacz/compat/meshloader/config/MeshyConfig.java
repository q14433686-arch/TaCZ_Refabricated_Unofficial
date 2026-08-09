package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。
 *
 * <p>挂在 TACZ 的客户端配置系统上（{@code ClientConfig.init} 调用
 * {@link #init}），与 {@code RenderConfig} 同一套 ForgeConfigSpec。</p>
 *
 * <p>性能相关开关说明：26.2 渲染管线没有 VBO 几何缓存，poly_mesh 顶点
 * 每帧由 CPU 重建，高面数模型（几万~几十万顶点）是主要开销。
 * 下面的开关用于把 poly 绘制裁剪到「必要的场合」：</p>
 * <ul>
 *   <li>{@code MeshPolyInShadow}：阴影 pass 默认不画 poly（立方体阴影足够，
 *       上游同款策略），可省约一半绘制量；</li>
 *   <li>{@code MeshMaxRenderDistance}：非第一人称（世界里的其他玩家/掉落物/
 *       方块）超过该距离不画 poly，0 = 不限制；</li>
 *   <li>{@code MeshPolyInPreview}：GUI/FIXED 预览上下文（工作台预览等）
 *       是否画 poly。</li>
 * </ul>
 */
public class MeshyConfig {

    /** 总开关。关闭后 poly_mesh 完全不绘制（立方体路径不受影响）。 */
    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;

    /** 阴影 pass 中是否绘制 poly。默认 false：阴影形状用立方体就足够。 */
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;

    /** 非第一人称上下文的 poly 渲染距离（方块）。0 = 不限制。 */
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;

    /** GUI/FIXED 预览上下文是否绘制 poly。 */
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;

    /** 加载时输出 mesh 统计（骨骼数/顶点数），便于排查性能。 */
    public static ForgeConfigSpec.BooleanValue LOG_STATS;

    /**
     * GPU 静态烘焙（每骨骼常驻 GpuBuffer，每帧只更新变换）。
     * 开启后第一人称高面数枪的每帧 CPU 顶点工作从 O(顶点) 降到 O(骨骼)；
     * Iris 光影包启用时自动回退 consumer 路径（不受本开关影响）。
     */
    public static ForgeConfigSpec.BooleanValue GPU_BAKING;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("mesh_loader");

        builder.comment("Master switch for TacZ Mesh Loader poly_mesh rendering. "
                + "Cube-only rendering is unaffected.");
        ENABLE_MESH = builder.define("MeshEnable", true);

        builder.comment("Whether to render poly_mesh during shadow passes. "
                + "Default false: the cube body already provides shadow shapes, "
                + "and skipping poly here roughly halves per-frame vertex work with shaders enabled.");
        POLY_IN_SHADOW = builder.define("MeshPolyInShadow", false);

        builder.comment("Maximum distance (blocks) to render poly_mesh in world contexts "
                + "(other players, dropped items, blocks). 0 = unlimited. "
                + "First-person view is always rendered in full.");
        MAX_RENDER_DISTANCE = builder.defineInRange("MeshMaxRenderDistance", 48.0, 0.0, 1_000_000.0);

        builder.comment("Whether to render poly_mesh in GUI/FIXED preview contexts "
                + "(e.g. gun workbench preview).");
        POLY_IN_PREVIEW = builder.define("MeshPolyInPreview", true);

        builder.comment("Log poly_mesh statistics (bone/vertex counts) when models load.");
        LOG_STATS = builder.define("MeshLogStats", true);

        builder.comment("GPU static baking: each bone's vertices are uploaded to a persistent "
                + "GpuBuffer once and only per-bone transforms are updated every frame. "
                + "Reduces per-frame CPU vertex work from O(vertices) to O(bones) for "
                + "first-person high-poly guns. Automatically falls back to the CPU path "
                + "when an Iris shader pack is active.");
        GPU_BAKING = builder.define("MeshGpuBaking", true);

        builder.pop();
    }

    private MeshyConfig() {
    }
}
