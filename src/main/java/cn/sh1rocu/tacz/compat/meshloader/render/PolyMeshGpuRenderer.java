package cn.sh1rocu.tacz.compat.meshloader.render;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * poly_mesh 的 GPU 静态烘焙渲染器（26.2 性能方案，第三版）。
 *
 * <h2>前两版为什么不行</h2>
 *
 * <ul>
 *   <li><b>PR #33（2026-08-09）</b>：画在世界 pass 的阶段边界、乘不可信的
 *       {@code RenderSystem.getModelViewMatrixCopy()}、{@code visitBones} 的
 *       skip 谓词写反剪掉整棵子树 —— 实机「枪看不见 + 严重卡顿」，PR 关闭。</li>
 *   <li><b>PR #69（2026-08-24，关闭）</b>：改对了 pass 与矩阵来源，但
 *       (1) 光影包启用时整条 GPU 路径关闭、回退到 consumer —— 36 万顶点级枪模
 *       在 consumer 路径下每帧全量重建，这正是要解决的卡顿；
 *       (2) 管线用 {@code EMISSIVE} 满亮烘焙，枪在暗处发光；
 *       (3) 深度附件那套 5 参 {@code createRenderPass} 从未编译验证过；
 *       (4) 从未跑过 {@code ./gradlew build}。</li>
 * </ul>
 *
 * <h2>这一版怎么改</h2>
 *
 * <ul>
 *   <li><b>真实光照</b>：不再 {@code EMISSIVE}。管线不带该 define，顶点 UV2 里
 *       烘焙 packedLight，片元采样光亮度表（{@code Sampler2}，
 *       {@link Minecraft} 的 {@code gameRenderer.levelLightmap()}）。
 *       光照档位变化（sky/block 各自 4 级一档）时按需重烘焙，两次重烘焙之间
 *       至少间隔 1 秒，避免在光照边界来回抖动时每帧重写顶点。</li>
 *   <li><b>深度遮挡</b>：5 参 {@code createRenderPass} 挂主 target 的深度视图、
 *       不清空（{@code OptionalDouble.empty()} = LOAD），配合
 *       {@code DepthStencilState.DEFAULT} —— 枪模被方块/自己的镜身正确遮挡。
 *       手部 pass 的深度在 {@code renderItemInHand} 之前被 vanilla 清 0
 *       （GameRenderer 字节码实读），世界 pass 的深度含地形与实体，两处都正确。</li>
 *   <li><b>光影包</b>：默认仍回退 consumer（Iris 在包启用时接管渲染目标，
 *       自建 pass 画进 vanilla 主 target 的结果不保证可见）。
 *       {@code MeshGpuUnderShaders=true} 可实验性强开，后果自担并写进日志。
 *       该判断读自 Iris 26.2 分支源码：
 *       {@code MixinGameRenderer#iris$disableVanillaHandRendering} 在包启用时
 *       跳过原版手部提交，由 Iris 自己的 {@code HandRenderer} 画手。</li>
 *   <li><b>纹理视图</b>：直接用 26.2 的 {@code AbstractTexture#getTextureView()}
 *       公开 getter，不再反射私有字段。</li>
 *   <li><b>失败退路</b>：GPU pass 抛异常 → 本次会话禁用并落回 consumer 路径，
 *       日志说明原因。满亮回退管线：光亮度表拿不到时退 {@code EMISSIVE} 满亮。</li>
 * </ul>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric 的 VBO 思路 (GPL-3.0)，
 * 按 26.2 GpuBuffer / RenderPass 重写。</p>
 *
 * <p><b>验证状态（按 AGENTS.md §2 如实标注）</b>：所有 MC API 引用已逐条对照
 * 本仓库已编译通过的在役代码（ScopeMaskRenderer / ScopePipRenderer /
 * ScopeBodyRenderTypes）与社区 26.2 反编译源核对；本沙箱无法运行
 * {@code ./gradlew build}，<b>本类尚未编译、尚未实机验证</b>。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TacZMeshLoader");

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    /**
     * 光照量化步长：sky/block 各自 {@code level & ~3}（0/4/8/12 四档）。
     * 档位变了才重烘焙；光照渐变时最多 4×4 = 16 档，实际一场对局通常只命中 2~4 档。
     */
    private static final int LIGHT_GRID = 4;

    /** 两次重烘焙之间的最小间隔（毫秒），防止在档位边界抖动时每帧重写顶点。 */
    private static final long REBAKE_MIN_INTERVAL_MS = 1000L;

    /**
     * 正常受光管线：vanilla {@code core/entity}，无 EMISSIVE（采样光亮度表）、
     * 无 overlay、无方向光（面已按 Meshy 的平直法线烘焙）、cutout、双面、深度默认。
     *
     * <p>配方对照本仓库 ScopeBodyRenderTypes 注释里逐指令抄录的 vanilla
     * {@code ENERGY_SWIRL} 字节码（同样以 {@code MATRICES_FOG_SNIPPET} 为底 +
     * 显式顶点绑定/拓扑/深度），去掉 EMISSIVE、补上 {@code BindGroupLayouts.SAMPLER2}
     * （光亮度表）。{@code ALPHA_CUTOUT} 带 float 阈值的写法与
     * {@code RenderPipeline builder} 的既有调用一致。</p>
     */
    private static final RenderPipeline LIT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    /**
     * 满亮回退管线：光亮度表视图拿不到时使用（与 PR #69 的配方一致）。
     * 枪在暗处会显得过亮 —— 是回退，不是等价实现。
     */
    private static final RenderPipeline EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity_emissive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    /** 一根骨骼的常驻顶点缓冲。顶点保持骨骼本地空间，UV2 = 烘焙时的量化光照。 */
    public static final class BakedBone {
        public final GpuBuffer vertexBuffer;
        public final int indexCount;

        BakedBone(GpuBuffer vertexBuffer, int indexCount) {
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
        }

        public void close() {
            // 与 ScopeMaskRenderer#close 同款：渲染线程上直接 close，
            // 该模式在本仓库在役代码里已实测安全。
            vertexBuffer.close();
        }
    }

    /** 一次 draw 登记：完整物品+骨骼矩阵（来自 submit 时的 poseStack），不乘任何 modelView。 */
    public record DrawEntry(Matrix4f model, Identifier texture, BakedBone bone) {
    }

    private static final List<DrawEntry> HAND_DRAWS = new ArrayList<>();
    private static final List<DrawEntry> WORLD_DRAWS = new ArrayList<>();

    private static boolean loggedFirstDraw = false;
    private static boolean loggedShaderPackFallback = false;
    private static boolean gpuDisabledThisSession = false;
    /** 光亮度表视图解析失败时置位，整场用 EMISSIVE 满亮管线。 */
    private static boolean lightmapUnavailable;

    private PolyMeshGpuRenderer() {
    }

    // =====================================================================
    // 门槛判定
    // =====================================================================

    /**
     * GPU 路径当前是否可用。
     *
     * <p>光影包启用时默认不可用（Iris 接管了渲染目标，自建 pass 的输出不保证
     * 出现在最终画面上，还可能画出「一把无光影一把有光影」的双枪）。
     * {@code MeshGpuUnderShaders} 供愿意承担视觉不一致的玩家实验性强开。</p>
     */
    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack() && !MeshyConfig.GPU_UNDER_SHADERS.get()) {
            if (!loggedShaderPackFallback) {
                loggedShaderPackFallback = true;
                LOGGER.info("[TacZMeshLoader] Shader pack active: poly guns use the CPU consumer path. "
                        + "High-poly guns (>100k verts) will cost heavy frame time in this mode. "
                        + "Set MeshGpuUnderShaders=true in tacz-client.toml [mesh_loader] to try the "
                        + "experimental GPU pass under shaders (gun body will NOT receive shader-pack lighting).");
            }
            return false;
        }
        return true;
    }

    /**
     * 把 packedLight 量化到 {@link #LIGHT_GRID} 档位，作为烘焙/重烘焙的 key。
     * 发光骨骼（illuminated）恒为满亮，不参与量化。
     *
     * <p><b>26.2 打包格式</b>：{@code LightCoordsUtil.pack(block, sky)} =
     * {@code block << 4 | sky << 20}（sky 在第 20 位，不是 1.20.1 的第 8 位；
     * 26.2 反编译源 {@code LightCoordsUtil.java} 实读）。拆包必须用同一布局。</p>
     */
    public static int quantizeLight(int packedLight) {
        int block = Math.min(15, Math.max(0, (packedLight >> 4) & 0xF));
        int sky = Math.min(15, Math.max(0, (packedLight >>> 20) & 0xF));
        int qb = (block / LIGHT_GRID) * LIGHT_GRID;
        int qs = (sky / LIGHT_GRID) * LIGHT_GRID;
        return LightCoordsUtil.pack(qb, qs);
    }

    // =====================================================================
    // 烘焙
    // =====================================================================

    /**
     * 烘焙一根骨骼：顶点以骨骼本地坐标一次性写入常驻 GpuBuffer，UV2 = 量化光照。
     *
     * @param meshes   该骨骼的全部 poly_mesh
     * @param lightKey {@link #quantizeLight} 的结果（发光骨骼应直接传 {@link #FULL_BRIGHT}）
     */
    public static BakedBone bakeBone(List<PolyMesh> meshes, int lightKey) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        // ENTITY 顶点单条 ≤ 44B（含对齐余量），多给一点避免 Builder 中途扩容搬移。
        ByteBufferBuilder scratch = new ByteBufferBuilder(vertexCount * 48L + 1024L);
        BufferBuilder builder = new BufferBuilder(scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
        for (PolyMesh mesh : meshes) {
            mesh.writeRaw(builder, lightKey);
        }
        MeshData meshData = builder.build();
        if (meshData == null) {
            scratch.close();
            return null;
        }
        try (meshData) {
            GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "tacz_mesh_bone", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            return new BakedBone(vertexBuffer, meshData.drawState().indexCount());
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to bake bone geometry", e);
            return null;
        } finally {
            scratch.close();
        }
    }

    // =====================================================================
    // 每帧登记与绘制
    // =====================================================================

    /**
     * @param bonePose submit 时 {@code poseStack.last().pose()} 的拷贝，
     *                 已经包含物品根变换 + 骨骼链，不要再乘 modelView。
     * @param texture  该枪模的贴图（一把枪一张；多贴图混排枪包按实际传入）。
     * @param handPass 当前 submit 是否发生在第一人称手部路径。
     */
    public static void submitBone(Matrix4f bonePose, Identifier texture, BakedBone bone, boolean handPass) {
        if (bone == null) {
            return;
        }
        DrawEntry entry = new DrawEntry(new Matrix4f(bonePose), texture, bone);
        if (handPass) {
            HAND_DRAWS.add(entry);
        } else {
            WORLD_DRAWS.add(entry);
        }
    }

    /** 每帧 extract 开头调用，避免某一 pass 没跑时登记表泄漏。 */
    public static void beginFrame() {
        HAND_DRAWS.clear();
        WORLD_DRAWS.clear();
    }

    /**
     * 在 {@code renderAllFeatures} 的阶段边界绘制本帧对应 pass 的骨骼。
     * 由 {@code FeatureRenderDispatcherMixin} 在 {@code executeAlwaysOnTop} 之后调用。
     *
     * <p>世界那次 {@code renderAllFeatures} 时主 target 深度含地形/实体（正确遮挡）；
     * 手部那次之前 vanilla 刚清过深度（GameRenderer 字节码实读），同样正确。</p>
     */
    public static void renderAtPhaseBoundary() {
        boolean handPass = ScopeMaskRenderer.isInHandPass();
        List<DrawEntry> draws = handPass ? HAND_DRAWS : WORLD_DRAWS;
        if (draws.isEmpty()) {
            return;
        }
        if (!handPass && ScopePipRenderer.redirectTarget() != null) {
            // 镜内画中画的「第二遍世界渲染」也会跑一次 renderAllFeatures，且此刻
            // mainRenderTarget 正被重定向到离屏 target。这里若画（并清空登记表），
            // 世界 mesh 枪就只剩镜内那一遍、主画面反而没有了。
            // 跳过且不清空，把登记表留给主画面那一遍。
            return;
        }
        try {
            drawList(draws, handPass);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] GPU mesh pass failed; falling back to consumer path for this session.", e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
        } finally {
            draws.clear();
        }
    }

    private static void drawList(List<DrawEntry> draws, boolean handPass) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return;
        }
        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.getDepthTextureView();
        if (colorView == null || depthView == null) {
            // 深度视图拿不到就不画：无深度测试的枪模会透墙/透镜身，宁可这帧没有 poly。
            return;
        }
        GpuTextureView lightmapView = resolveLightmap(mc);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_mesh_gpu",
                colorView,
                Optional.empty(),
                depthView,
                OptionalDouble.empty())) {
            boolean lit = lightmapView != null;
            pass.setPipeline(lit ? LIT_PIPELINE : EMISSIVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            if (lit) {
                // 光亮度表：16×16 纹理，NEAREST + clamp 与 vanilla 语义一致
                //（sample_lightmap.glsl 里已做半像素钳制）。
                pass.bindTexture("Sampler2", lightmapView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }

            for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
                GpuTextureView textureView = resolveTextureView(group.getKey());
                if (textureView == null) {
                    textureView = resolveTextureView(MissingTextureAtlasSprite.getLocation());
                }
                if (textureView == null) {
                    continue;
                }
                pass.bindTexture("Sampler0", textureView, linearSampler);

                for (DrawEntry entry : group.getValue()) {
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(entry.model(), WHITE));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer.slice());
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                    // 参数顺序照本仓库在役 ScopeMaskRenderer 抄录的 vanilla 字节码：
                    // drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, 0)
                    pass.drawIndexed(entry.bone().indexCount, 1, 0, 0, 0);
                }
            }
            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                long indices = 0;
                for (DrawEntry entry : draws) {
                    indices += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) on {} pass, lit={}",
                        draws.size(), indices, handPass ? "hand" : "world", lit);
            }
        }
    }

    private static GpuTextureView resolveTextureView(Identifier texture) {
        try {
            // 26.2 公开 getter；TextureManager#getTexture 缺失时会现场加载。
            return Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to resolve texture view for {}", texture, e);
            return null;
        }
    }

    private static GpuTextureView resolveLightmap(Minecraft mc) {
        if (lightmapUnavailable) {
            return null;
        }
        try {
            GpuTextureView view = mc.gameRenderer.levelLightmap();
            if (view == null) {
                lightmapUnavailable = true;
                LOGGER.warn("[TacZMeshLoader] Level lightmap view unavailable; GPU path falls back to "
                        + "EMISSIVE full-bright (guns will look over-lit in dark areas).");
            }
            return view;
        } catch (Throwable t) {
            lightmapUnavailable = true;
            LOGGER.warn("[TacZMeshLoader] Failed to read level lightmap; GPU path falls back to "
                    + "EMISSIVE full-bright (guns will look over-lit in dark areas).", t);
            return null;
        }
    }
}
