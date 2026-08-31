package cn.sh1rocu.tacz.compat.meshloader.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TacZ Mesh Loader 客户端配置。挂在 TACZ 的 {@code ClientConfig} 上。
 *
 * <h2>范围</h2>
 * <p>collector（VertexConsumer）渲染路径 + 解析缓存 + 顶点预算闸门，
 * 外加 GPU 静态烘焙（{@code MeshGpuBaking} 起总闸，见 {@code PolyMeshGpuRenderer}）：
 * 手部 pass（第 1/2 步）与世界 pass（第 3 步，{@code MeshGpuWorld}）各一张表、
 * 各自在自己的 flush 处消费；GUI / 预览 / 镜内 / 阴影由<b>提交侧</b>闸门挡在表外
 * —— 关 PR #33/#69/#70/#71 的「世界 pass 泄漏」正是提交侧没闸门 + 绘制时矩阵取自
 * 错误时刻两件事叠出来的。光影下两条路（{@code MeshGpuUnderShaders} /
 * {@code MeshGpuWorldUnderShaders}）自 R3 起默认开 —— 两条都在 2026-08-31 由维护者实机 PASS；
 * 失联/异常时仍然各自静默回 collector，所以「默认开」的下界是「和关着一样」。详见
 * {@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md}。</p>
 */
public final class MeshyConfig {

    public static ForgeConfigSpec.BooleanValue ENABLE_MESH;
    public static ForgeConfigSpec.BooleanValue POLY_MIRROR_REVERSE_WINDING;
    public static ForgeConfigSpec.BooleanValue POLY_INVERT_NORMALS;
    public static ForgeConfigSpec.BooleanValue POLY_PREFER_PACK_NORMALS;
    public static ForgeConfigSpec.BooleanValue POLY_ILLUMINATED_REAL_SKY;
    public static ForgeConfigSpec.BooleanValue POLY_IN_SHADOW;
    public static ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue POLY_IN_PREVIEW;
    public static ForgeConfigSpec.BooleanValue LOG_STATS;
    public static ForgeConfigSpec.BooleanValue GPU_BAKING;
    public static ForgeConfigSpec.BooleanValue GPU_UNDER_SHADERS;
    public static ForgeConfigSpec.BooleanValue GPU_WORLD;
    public static ForgeConfigSpec.BooleanValue GPU_WORLD_UNDER_SHADERS;
    public static ForgeConfigSpec.IntValue GPU_LIGHT_CACHE_SIZE;
    public static ForgeConfigSpec.IntValue GUI_MAX_VERTICES;
    public static ForgeConfigSpec.IntValue WORLD_MAX_VERTICES;
    public static ForgeConfigSpec.DoubleValue WORLD_FULL_DETAIL_DISTANCE;
    public static ForgeConfigSpec.IntValue MAX_MODEL_VERTICES;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("mesh_loader");

        builder.comment("Master switch for TacZ Mesh Loader poly_mesh rendering.",
                "Cube-only rendering is unaffected.");
        ENABLE_MESH = builder.define("MeshEnable", true);

        builder.comment("poly_mesh only: these three decide how mesh normals/winding are baked.",
                "They only matter with a shader pack installed (vanilla's entity program",
                "ignores va_normal), and they take effect when models are re-parsed (F3+T).",
                "MeshPolyMirrorReverseWinding: the poly format mirrors positions on one axis",
                "(Y), which makes every face's outward side become the back side. Shader packs",
                "that flip normals by gl_FrontFacing then light the gun inside-out. Reversing the",
                "winding on mirror is what vanilla TaCZ's own Bedrock cube path does for mirrors,",
                "so this stays on; turn it off only if a pack was authored for the old winding.",
                "MeshPolyInvertNormals: extra global negation of the baked normals. Try it if",
                "specular still shows on the wrong side with the option above at both settings.",
                "MeshPolyPreferPackNormals: use the per-vertex normals shipped in the pack",
                "(smooth shading) instead of one flat normal per face. Default off because that",
                "is what upstream does; packs with authored normals look noticeably better on.");
        POLY_MIRROR_REVERSE_WINDING = builder.define("MeshPolyMirrorReverseWinding", true);
        POLY_INVERT_NORMALS = builder.define("MeshPolyInvertNormals", false);
        POLY_PREFER_PACK_NORMALS = builder.define("MeshPolyPreferPackNormals", false);

        builder.comment("Bones whose name ends with _illuminated (self-lit reticles, lasers,",
                "mesh bodies authored that way) are baked at max block AND max sky light -",
                "that is how vanilla TaCZ's BedrockPart#render does it, and it is what keeps",
                "those parts visible in a pitch dark cave (vanilla multiplies the block and sky",
                "columns of the lightmap, so sky=0 would render them black).",
                "Shader packs read the *sky* nibble as 'this surface can see the sun/moon', so a",
                "constant 15 means no roof or wall can ever shade them: the gun body inherits the",
                "sky brightness day and night. With this on (and only while a shader pack is",
                "active), the sky nibble comes from the surrounding light instead, while block",
                "stays at 15 - still visible in the dark, no longer sun-lit through a ceiling.",
                "Applies to the poly layer; reload with F3+T (the GPU bake regenerates when the",
                "shader state flips.");
        POLY_ILLUMINATED_REAL_SKY = builder.define("MeshPolyIlluminatedRealSky", true);

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

        builder.comment("Master switch for GPU static baking: vertices stay in bone-local",
                "space in a resident VBO; each frame uploads O(bones) matrices instead of",
                "transforming every vertex on the CPU.",
                "First-person hands always use it when this is on; in-world contexts need",
                "MeshGpuWorld too. GUI/preview/shadow/in-scope submits are refused at the",
                "submit side, so they can never leak into the world pass (that was the",
                "closed PRs' wrong-screenshot bug).",
                "Falls back to the collector path if the GPU pass fails.");
        GPU_BAKING = builder.define("MeshGpuBaking", true);

        builder.comment("Keep the GPU-baked mesh gun on the resident-VBO path when a shader pack is",
                "active. The pass is opened inside Iris' own hand flush, so it lands in the gbuffer",
                "and is lit by the pack's gbuffers_hand program (the pipeline is registered with",
                "IrisApi.assignPipeline(HAND)). Needs an audited Iris 1.10.x; if the flush hook is",
                "not live the path refuses submissions and the gun keeps the collector route - that",
                "fallback is why this can default to on. In-game PASS with a shader pack 2026-08-31.",
                "See docs/TML_GPU_STEP2_HANDFLUSH_20260831.md.");
        GPU_UNDER_SHADERS = builder.define("MeshGpuUnderShaders", true);

        builder.comment("GPU static baking for WORLD contexts too: third-person guns held by",
                "other players, dropped items, item frames and display statues draw from the same",
                "resident VBOs (O(bones) matrix uploads per gun per frame instead of transforming",
                "every vertex on the CPU). This is what makes a server full of high-poly mesh guns",
                "playable. Light is served by a small per-light-level VBO cache per model",
                "(MeshGpuLightCacheSize). The pass is opened right after the world's own feature",
                "flush, so it uses the same model-view matrix the collector batches were about to",
                "use -- GUI contexts never enter this table (see PolyMeshGpuRenderer).",
                "Requires MeshGpuBaking; falls back to the collector path if the pass fails or the",
                "flush hook is not live.");
        GPU_WORLD = builder.define("MeshGpuWorld", true);

        builder.comment("Also keep world mesh guns on the resident-VBO path under a shader pack.",
                "The world pass is lit through the pack's entity program: the custom pipeline is",
                "registered with IrisApi.assignPipeline(IrisProgram.ENTITIES) (constant audited",
                "against the Iris 1.10.7 jar via CI javap - EMISSIVE_ENTITIES is deliberately not",
                "used). In-game PASS 2026-08-31. Like the hand path it needs the audited Iris flush",
                "hook and refuses submissions when that hook is not live.");
        GPU_WORLD_UNDER_SHADERS = builder.define("MeshGpuWorldUnderShaders", true);

        builder.comment("How many quantized light levels of baked world VBOs to keep per gun model",
                "(LRU). Upstream TML caches 8 unquantized levels; this port quantizes light first",
                "(4 steps for block/sky each), so 4 levels cover nearly every scene. Every cached",
                "level costs GPU memory proportional to the model's vertex count.",
                "First-person baking is unaffected (it keeps a single level).");
        GPU_LIGHT_CACHE_SIZE = builder.defineInRange("MeshGpuLightCacheSize", 4, 1, 16);

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
