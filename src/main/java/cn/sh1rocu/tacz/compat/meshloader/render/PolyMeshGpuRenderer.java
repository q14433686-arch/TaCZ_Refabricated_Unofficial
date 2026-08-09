package cn.sh1rocu.tacz.compat.meshloader.render;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
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
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * poly_mesh 的 GPU 静态烘焙渲染器（26.2 性能方案）。
 *
 * <h2>为什么需要它</h2>
 * 26.2 渲染管线没有 VBO 几何缓存：consumer 路径每帧在 CPU 上重建全部顶点。
 * 高面数 mesh（实测 ak_enact 36 万顶点、mcx_virtus 30 万顶点）会把每帧
 * CPU 顶点工作推到数十毫秒 —— 这就是「模型越精细越卡」（30 帧）的根因。
 *
 * <h2>原理</h2>
 * <ul>
 *   <li>每骨骼的顶点（骨骼本地空间）<b>一次性</b>上传为常驻 {@link GpuBuffer}；</li>
 *   <li>每帧 submit 阶段只收集每骨骼的变换（矩阵 + 法线矩阵，O(骨骼)）；</li>
 *   <li>在 feature 渲染的阶段边界，用原始 GPU pass 画到主屏幕
 *       {@code GameRenderer#mainRenderTarget()}（带深度附件，与世界正确遮挡）；</li>
 *   <li>顶点位置由 shader 的 {@code ModelViewMat}（= 引擎 modelView × 根 × 骨骼）
 *       变换；法线由自建 {@code MeshNormalMat} uniform（= 根 × 骨骼的法线矩阵）
 *       变换 —— 与 consumer 路径（CPU 侧 {@code pose.normal()} × 本地法线）
 *       完全等价，光照一致。</li>
 * </ul>
 *
 * <p>每帧 CPU 成本从 O(顶点) 降到 O(骨骼 × 一次 draw)。</p>
 *
 * <h2>回退</h2>
 * Iris 光影包启用时（{@code IrisCompat#isUsingRenderPack()}）主屏幕 target
 * 被光影接管，本路径自动禁用、回退 consumer 路径（正确性优先）。
 *
 * <p>基于移植版 {@code ScopeMaskRenderer}（离屏掩码 pass）验证过的
 * 26.2 原始 GPU 绘制模式扩展：本类是它「画到主屏幕 + 贴图 + 深度」的版本。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    /** 烘焙进顶点的光照：满亮（15728880 = 天光15 + 块光15）。 */
    public static final int BAKED_LIGHT = 15728880;

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    // =========================================================================
    // 管线
    // =========================================================================

    /** 自建 uniform：每骨骼的法线矩阵（std140 mat3 = 48 字节）。 */
    private static final String NORMAL_UNIFORM = "MeshNormalMat";

    private static final BindGroupLayout NORMAL_LAYOUT =
            BindGroupLayout.builder().withUniform(NORMAL_UNIFORM, UniformType.UNIFORM_BUFFER).build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity"))
            .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/mesh_entity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/mesh_entity"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(NORMAL_LAYOUT)
            .withCull(false)
            // 关键：必须显式声明深度/颜色状态。
            // 26.2 里 pipeline 不声明深度状态（depthStencilState==null）＝
            // 「不需要深度附件」，而我们的 pass 却带了 mainRenderTarget 的深度
            // 附件 —— 声明与实际不符时绘制会被引擎静默丢弃（ScopeMaskRenderer
            // 注释里记录过同款事故的反向情形），表现就是「枪完全不渲染、无任何报错」。
            // DEFAULT 是 vanilla 实体管线的标准状态（深度测试 + 写深度 + 标准颜色输出）。
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    // =========================================================================
    // 烘焙数据
    // =========================================================================

    /** 一个骨骼的常驻 GPU 几何（含法线矩阵 uniform 缓冲，每帧只更新内容）。 */
    public record BakedBone(GpuBuffer vertexBuffer, GpuBuffer normalUniform, int indexCount, Identifier texture) {
        public void close() {
            if (vertexBuffer != null) {
                // GPU 资源释放必须在渲染线程
                RenderSystem.queueFencedTask(vertexBuffer::close);
            }
            if (normalUniform != null) {
                RenderSystem.queueFencedTask(normalUniform::close);
            }
        }
    }

    /** 每帧登记的一个绘制项：骨骼变换（冻结）+ 常驻几何。 */
    public record DrawEntry(Matrix4f modelView, Matrix3f normal, BakedBone bone) {
    }

    private static final List<DrawEntry> DRAW_LIST = new ArrayList<>();
    /** 首次绘制成功的统计日志只打一次。 */
    private static boolean loggedFirstDraw = false;

    // =========================================================================
    // 纹理 view 解析
    // =========================================================================

    private static final Map<Identifier, GpuTextureView> TEXTURE_VIEW_CACHE = new HashMap<>();
    private static Field textureViewField;

    /** 从 TextureManager 已注册纹理反射取 GpuTextureView（AbstractTexture.textureView 为 protected）。 */
    public static GpuTextureView getTextureView(Identifier texture) {
        GpuTextureView cached = TEXTURE_VIEW_CACHE.get(texture);
        if (cached != null) {
            return cached;
        }
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (tex == null) {
                return null;
            }
            if (textureViewField == null) {
                textureViewField = AbstractTexture.class.getDeclaredField("textureView");
                textureViewField.setAccessible(true);
            }
            GpuTextureView view = (GpuTextureView) textureViewField.get(tex);
            if (view != null) {
                TEXTURE_VIEW_CACHE.put(texture, view);
            }
            return view;
        } catch (Exception e) {
            GunMod.LOGGER.error("[TacZMeshLoader] Failed to resolve texture view for {}", texture, e);
            return null;
        }
    }

    public static void clearTextureViewCache() {
        TEXTURE_VIEW_CACHE.clear();
    }

    // =========================================================================
    // 路径开关
    // =========================================================================

    /** GPU 烘焙路径当前是否可用（配置开启且未启用 Iris 光影包）。 */
    public static boolean isGpuPathUsable() {
        return MeshyConfig.GPU_BAKING.get() && !IrisCompat.isUsingRenderPack();
    }

    // =========================================================================
    // 烘焙（渲染线程）
    // =========================================================================

    /**
     * 把一个骨骼的网格烘焙成常驻 GPU 缓冲。必须在渲染线程调用。
     * 顶点保持骨骼本地空间（与 consumer 路径的 bakedX/Y/Z 一致），
     * 变换由 shader 的 ModelViewMat / MeshNormalMat 完成。
     */
    public static BakedBone bakeBone(List<PolyMesh> meshes, Identifier texture) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        ByteBufferBuilder scratch = new ByteBufferBuilder(vertexCount * 44 + 4096);
        BufferBuilder builder = new BufferBuilder(scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
        for (PolyMesh mesh : meshes) {
            mesh.writeRaw(builder, BAKED_LIGHT);
        }
        MeshData meshData = builder.build();
        if (meshData == null) {
            scratch.close();
            return null;
        }
        try (meshData) {
            GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "tacz_mesh_bone", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            // 常驻法线矩阵 uniform 缓冲（48 字节 std140 mat3），每帧只更新内容、不重建。
            GpuBuffer normalUniform = RenderSystem.getDevice().createBuffer(
                    () -> "tacz_mesh_normal", GpuBuffer.USAGE_UNIFORM,
                    ByteBuffer.allocate(48).order(ByteOrder.nativeOrder()));
            return new BakedBone(vertexBuffer, normalUniform, meshData.drawState().indexCount(), texture);
        } catch (Exception e) {
            GunMod.LOGGER.error("[TacZMeshLoader] Failed to bake bone geometry", e);
            return null;
        }
    }

    // =========================================================================
    // submit 阶段登记（渲染线程）
    // =========================================================================

    /**
     * 登记一个骨骼的当帧变换。在模型 submit 时调用（渲染线程），
     * 变换为当帧实时值；真正绘制发生在阶段边界。
     */
    public static void submitBone(Matrix4f frozenModelView, Matrix3f frozenNormal, BakedBone bone) {
        if (bone != null) {
            DRAW_LIST.add(new DrawEntry(frozenModelView, frozenNormal, bone));
        }
    }

    // =========================================================================
    // 阶段边界绘制（渲染线程）
    // =========================================================================

    /**
     * 在 feature 渲染阶段边界绘制本帧登记的骨骼。
     * 画到主屏幕 target（颜色 + 深度），不清空 —— 与世界正确遮挡。
     * 由 {@code FeatureRenderDispatcherMixin} 调用。
     */
    public static void renderAtPhaseBoundary() {
        if (DRAW_LIST.isEmpty()) {
            return;
        }
        try {
            RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (mainTarget == null) {
                return;
            }
            GpuTextureView colorView = mainTarget.getColorTextureView();
            GpuTextureView depthView = mainTarget.getDepthTextureView();
            GpuTextureView lightmapView = Minecraft.getInstance().gameRenderer.lightmap();
            GpuTextureView overlayView = Minecraft.getInstance().gameRenderer.overlayTexture().getTextureView();
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            // 按贴图分组，减少 bindTexture 切换
            Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
            for (DrawEntry entry : DRAW_LIST) {
                byTexture.computeIfAbsent(entry.bone().texture(), k -> new ArrayList<>()).add(entry);
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            // 在开 pass 之前统一更新所有骨骼的法线 uniform 缓冲
            // （writeToBuffer 是 encoder 命令，放 pass 外记录更安全）
            for (DrawEntry entry : DRAW_LIST) {
                writeNormalMatrix(encoder, entry.bone().normalUniform(), entry.normal());
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_mesh_gpu",
                    colorView,
                    Optional.empty(),
                    depthView,
                    OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);

                GpuBufferSlice lights = RenderSystem.getShaderLights();
                if (lights != null) {
                    pass.setUniform("Lighting", lights);
                }

                for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
                    GpuTextureView textureView = getTextureView(group.getKey());
                    if (textureView == null) {
                        textureView = getTextureView(MissingTextureAtlasSprite.getLocation());
                    }
                    if (textureView == null) {
                        continue;
                    }
                    pass.bindTexture("Sampler0", textureView, sampler);
                    pass.bindTexture("Sampler1", overlayView, sampler);
                    pass.bindTexture("Sampler2", lightmapView, sampler);

                    for (DrawEntry entry : group.getValue()) {
                        pass.setUniform("DynamicTransforms",
                                RenderSystem.getDynamicUniforms().writeTransform(entry.modelView(), WHITE));
                        pass.setUniform(NORMAL_UNIFORM, entry.bone().normalUniform().slice());
                        pass.setVertexBuffer(0, entry.bone().vertexBuffer().slice());
                        RenderSystem.AutoStorageIndexBuffer indices =
                                RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                        pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount()), indices.type());
                        pass.drawIndexed(entry.bone().indexCount(), 1, 0, 0, 0);
                    }
                }
                if (!loggedFirstDraw) {
                    loggedFirstDraw = true;
                    int bones = DRAW_LIST.size();
                    long indices = 0;
                    for (DrawEntry entry : DRAW_LIST) {
                        indices += entry.bone().indexCount();
                    }
                    GunMod.LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) to main target.",
                            bones, indices);
                }
            } catch (Exception e) {
                GunMod.LOGGER.error("[TacZMeshLoader] GPU mesh pass failed; falling back to consumer path.", e);
                MeshyConfig.GPU_BAKING.set(false);
            }
        } finally {
            DRAW_LIST.clear();
        }
    }

    /**
     * 把 3x3 法线矩阵写入骨骼的常驻 uniform 缓冲（std140 mat3，48 字节，
     * 列主序，每列 16 字节对齐）。零分配：缓冲在烘焙时创建，每帧仅覆写内容。
     */
    private static void writeNormalMatrix(CommandEncoder encoder, GpuBuffer buffer, Matrix3f normal) {
        ByteBuffer data = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder());
        FloatBuffer f = data.asFloatBuffer();
        f.put(normal.m00()).put(normal.m10()).put(normal.m20()).put(0f);
        f.put(normal.m01()).put(normal.m11()).put(normal.m21()).put(0f);
        f.put(normal.m02()).put(normal.m12()).put(normal.m22()).put(0f);
        data.rewind();
        encoder.writeToBuffer(buffer.slice(), data);
    }

    private PolyMeshGpuRenderer() {
    }
}
