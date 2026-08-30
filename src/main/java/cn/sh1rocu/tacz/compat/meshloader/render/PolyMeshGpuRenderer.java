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
 * poly_mesh 的 GPU 静态烘焙渲染器 —— <b>仅第一人称手部 pass</b>。
 *
 * <h2>为什么前几次内置失败、这次怎么改</h2>
 *
 * <p>26.2 的 {@code entity.vsh} 是 {@code ProjMat * ModelViewMat * Position}。
 * 立方体路径把 submit 时的 pose 烘焙进顶点，再以 identity PoseStack 交给 collector，
 * 绘制时 ModelViewMat = I。GPU 路径把顶点留在骨骼本地、把同一份 pose 写成
 * DynamicTransforms.ModelViewMat，数学上等价。</p>
 *
 * <p>关 PR（#33/#69/#70/#71）的两条硬伤不在这套代数上：</p>
 * <ol>
 *   <li><b>全局 WORLD_DRAWS 表</b>：GUI / 掉落物 / 展示框 / 第三人称的 submit
 *       也被登记，然后在<b>世界</b>那次 {@code renderAllFeatures} 用世界投影画出去。
 *       这就是「帖图不对」——枪出现在错误的 pass / 投影里。</li>
 *   <li><b>弹匣</b>：没接 26.2 的 {@code IMirrorGeometry}，纯 mesh 弹匣在主路径里
 *       被漏画。</li>
 * </ol>
 *
 * <p>这次 GPU 表<b>只收第一人称手部 pass 当时登记的骨骼</b>，并且只在
 * {@code inHandPass=true} 的 {@code renderAllFeatures} 里、{@code executeSolid}
 * <b>之后</b>画（立方体已经进深度，投影是手部 FOV）。世界/GUI 全部走 collector。</p>
 *
 * <h2>管线配方（对照 26.2 jar 逐符号核对）</h2>
 * <p>底子用 {@code MATRICES_FOG_SNIPPET}（Globals + DynamicTransforms + Projection
 * + Fog —— 与 {@code ScopeMaskRenderer.MASK_PIPELINE} 同一理由：core shader 的
 * apply_fog 引用 Fog uniform 块，手拼 layout 少一个就编译不过）。shader 用 vanilla
 * {@code core/entity}：defines 取 {@code ALPHA_CUTOUT 0.1 + NO_OVERLAY +
 * NO_CARDINAL_LIGHTING}，即「顶点色直通 + lightmap 采样」——法线方向光在枪模上
 * 本来就被 NO_CARDINAL 掉（与 collector 的 entityCutout 视觉差异只有 overlay，
 * 枪不受伤没有 overlay）。lightmap 拿不到时退化 EMISSIVE 管线（不采 Sampler2）。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TacZMeshLoader");

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final int LIGHT_GRID = 4;

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

    /** 一根骨骼烘出的常驻 VBO。顶点是骨骼本地坐标，light 已按量化档烘进 UV2。 */
    public static final class BakedBone {
        public final GpuBuffer vertexBuffer;
        public final int indexCount;

        BakedBone(GpuBuffer vertexBuffer, int indexCount) {
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
        }

        public void close() {
            vertexBuffer.close();
        }
    }

    public record DrawEntry(Matrix4f model, Identifier texture, BakedBone bone) {
    }

    /** 仅第一人称手部。世界/GUI 禁止写入。 */
    private static final List<DrawEntry> HAND_DRAWS = new ArrayList<>();

    private static boolean loggedFirstDraw = false;
    private static boolean loggedShaderPackFallback = false;
    private static boolean gpuDisabledThisSession = false;
    private static boolean lightmapUnavailable;

    private PolyMeshGpuRenderer() {
    }

    /**
     * 当前这次 submit 是否该走 GPU。必须同时满足：
     * <ul>
     *   <li>配置打开且本会话未因异常关闭；</li>
     *   <li>光影包未启用（或实验开关强开）；</li>
     *   <li><b>现在就在手部 pass 里</b>——用 {@code ScopeMaskRenderer.isInHandPass()}，
     *       而不是 {@code transformType.firstPerson()}。后者对「用第一人称上下文
     *       画 GUI」这类路径也会为 true，正是关 PR WORLD_DRAWS 泄漏的入口。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpu() {
        if (!isGpuPathUsable()) {
            return false;
        }
        return ScopeMaskRenderer.isInHandPass();
    }

    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack() && !MeshyConfig.GPU_UNDER_SHADERS.get()) {
            if (!loggedShaderPackFallback) {
                loggedShaderPackFallback = true;
                LOGGER.info("[TacZMeshLoader] Shader pack active: first-person poly guns use the collector path. "
                        + "High-poly guns will cost frame time. Set MeshGpuUnderShaders=true to try the "
                        + "experimental GPU pass (gun body will NOT receive shader-pack lighting).");
            }
            return false;
        }
        return true;
    }

    /**
     * 光照 4 级量化：光照烘在顶点里，逐帧光照变化本会逼着逐帧重烘，
     * 量化 + {@code ensureBaked} 的 1 秒节流把重烘频率压到「跨光照档才发生」。
     */
    public static int quantizeLight(int packedLight) {
        int block = Math.min(15, Math.max(0, (packedLight >> 4) & 0xF));
        int sky = Math.min(15, Math.max(0, (packedLight >>> 20) & 0xF));
        int qb = (block / LIGHT_GRID) * LIGHT_GRID;
        int qs = (sky / LIGHT_GRID) * LIGHT_GRID;
        return LightCoordsUtil.pack(qb, qs);
    }

    public static BakedBone bakeBone(List<PolyMesh> meshes, int lightKey) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        // ENTITY 格式 stride 36 字节，预留些余量避免 grow。
        long capacity = vertexCount * 48L + 1024L;
        ByteBufferBuilder scratch = new ByteBufferBuilder((int) Math.min(capacity, Integer.MAX_VALUE));
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

    public static void submitBone(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        HAND_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /** 挂在 {@code GameRenderer#extract} HEAD（与 ScopeMaskRenderer.beginFrame 同点）。 */
    public static void beginFrame() {
        HAND_DRAWS.clear();
    }

    /**
     * 在手部 {@code renderAllFeatures} 的 {@code executeSolid} <b>之后</b>绘制。
     * 世界那次直接清空残留（理论上不应有）。
     */
    public static void renderAfterSolid() {
        if (!ScopeMaskRenderer.isInHandPass()) {
            HAND_DRAWS.clear();
            return;
        }
        if (HAND_DRAWS.isEmpty()) {
            return;
        }
        try {
            drawList(HAND_DRAWS);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] GPU mesh pass failed; falling back to collector path for this session.", e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
        } finally {
            HAND_DRAWS.clear();
        }
    }

    private static void drawList(List<DrawEntry> draws) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return;
        }
        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.getDepthTextureView();
        if (colorView == null || depthView == null) {
            return;
        }
        GpuTextureView lightmapView = resolveLightmap(mc);
        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        // 【朝向恒北 bug 的修复 · 字节码依据】RenderType.prepare() 偏移 55-58：
        //     getModelViewMatrixCopy() -> writeDynamicTransforms(mv)
        // vanilla 画 collector 提交是【两层】变换：顶点里烘 submit 时的 pose，
        // 绘制时 ModelViewMat = RenderSystem 当刻的 MV（手部 pass = 相机旋转/俯仰
        // /视模那套）。GPU 路径顶点在骨骼本地系、pose 写进 DynamicTransforms，
        // 若只写 entry.model() 就丢了 MV_draw 这一层 —— 枪位置对（pose 带平移）
        // 但朝向恒北、俯仰为平（相机旋转全在丢的那层里），实测症状完全吻合。
        // 本方法跑在手部 renderAllFeatures 的 executeSolid 之后、同一调用内，
        // MV 与 prepareFrame 时一致，取一次全体通用。
        Matrix4f handModelView = RenderSystem.getModelViewMatrixCopy();

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // 阶段边界不在任何 render pass 内（FeatureRenderDispatcherMixin 的字节码分析），
        // createRenderPass 的 isInRenderPass 断言安全。颜色 Optional.empty() = 不清屏，
        // 深度 OptionalDouble.empty() = 不清深度 —— executeSolid 画好的立方体深度要留着。
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
                    // ModelViewMat = MV_draw * pose_submit（乘序同 vanilla：顶点先套
                    // pose 再进相机系）。scratch 每骨骼重算，不污染 entry.model()。
                    Matrix4f mv = new Matrix4f(handModelView).mul(entry.model());
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(mv, WHITE));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer.slice());
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                    pass.drawIndexed(entry.bone().indexCount, 1, 0, 0, 0);
                }
            }
            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                long indices = 0;
                for (DrawEntry entry : draws) {
                    indices += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) on hand pass, lit={}",
                        draws.size(), indices, lit);
            }
        }
    }

    private static GpuTextureView resolveTextureView(Identifier texture) {
        try {
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
                LOGGER.warn("[TacZMeshLoader] Level lightmap view unavailable; GPU path falls back to EMISSIVE.");
            }
            return view;
        } catch (Throwable t) {
            lightmapUnavailable = true;
            LOGGER.warn("[TacZMeshLoader] Failed to read level lightmap; GPU path falls back to EMISSIVE.", t);
            return null;
        }
    }
}
