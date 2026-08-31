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
 * {@code MeshGpuWorldUnderShaders}）的默认值走过三轮：R3 开发期默认开 → 维护者实机
 * 发现「高模枪挡住太阳/月亮的那部分几何会继承天体的自发光亮度」（只有把这两个键关掉才消失，
 * 第一人称、第三人称、展示台三种语境一致）→ 一度改回默认关 → <b>R3 定稿（2026-09-01
 * 维护者裁定）默认开</b>：常驻 VBO 在光影下的收益仍胜过每帧 CPU 重变换，亮度继承是已知、
 * 可观测、随时可以整键关闭回退的取舍，不再作为默认关的理由。附带修掉一条相关缺陷：以前「拿不到 lightmap」会一次性闩锁并把
 * 整条路退化到 {@code EMISSIVE} 管线，而那条管线在光影包眼里是「自发光、不受阴影」；现在
 * 光影下拿不到 lightmap 直接退回 collector（见 {@code PolyMeshGpuRenderer#gpuMasterUsable}）。
 * 失联/异常时两条路也各自静默回 collector。详见 {@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md}
 * 与 {@code docs/MESH_LOADER.md} §5.9-§5.10。</p>
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
                "MeshPolyMirrorReverseWinding: mirror (Y flip) also reverses the emitted triangle",
                "order, so front and back swap. That is what BedrockPolygon does for mirrors, and",
                "this defaulted to ON for one round - but the collector path draws through",
                "RenderTypes.entityCutout, which culls back faces: reversing the winding then hides",
                "the outward faces and the gun comes out inside-out (near-black, highlights on the",
                "far walls). Maintainer side-by-side vs the Forge reference (2026-08-31): OFF is",
                "what matches. Keep it off; only turn it on for a pack authored the other way, or to",
                "compare on the GPU path (its pipelines disable culling, so there it is subtle).",
                "MeshPolyInvertNormals: extra global negation of the baked normals. Try it if",
                "specular still shows on the wrong side with the option above at both settings.",
                "MeshPolyPreferPackNormals: use the per-vertex normals shipped in the pack",
                "(smooth shading) instead of one flat normal per face. Default off because that",
                "is what upstream does (upstream has the same branch, compiled out by a constant,",
                "so enabling it is not a divergence from upstream); packs with authored normals",
                "look noticeably better on.");
        POLY_MIRROR_REVERSE_WINDING = builder.define("MeshPolyMirrorReverseWinding", false);
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
                "shader state flips).",
                "Default off: this was written against an early reading of the shader report and the",
                "actual cause turned out to be something else (see docs/MESH_LOADER.md 5.9), so it",
                "stays an opt-in until somebody confirms it looks better with a pack on.");
        POLY_ILLUMINATED_REAL_SKY = builder.define("MeshPolyIlluminatedRealSky", false);

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
                "not live the path refuses submissions and the gun keeps the collector route.",
                "ON by default as of the R3 final call (2026-09-01): the known caveat is that mesh",
                "gun geometry covering the sun/moon can inherit their brightness under a pack (the",
                "maintainer saw it on this very switch; turning this and MeshGpuWorldUnderShaders",
                "off removes it). That trade-off is accepted for the performance win; switch both",
                "off if it shows up badly with your pack. See docs/MESH_LOADER.md 5.9-5.10 and",
                "docs/TML_GPU_STEP2_HANDFLUSH_20260831.md.");
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
                "used). Like the hand path it needs the audited Iris flush hook and refuses",
                "submissions when that hook is not live. ON by default as of the R3 final call for",
                "the same reason as MeshGpuUnderShaders; the same sun/moon brightness caveat applies",
                "(it showed in third person and on display stands too) - switch both off to revert.");
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
