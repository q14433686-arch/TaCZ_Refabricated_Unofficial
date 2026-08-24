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
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * poly_mesh 的 GPU 静态烘焙渲染器（26.2 性能方案，第二版）。
 *
 * <h2>上一版为什么失败</h2>
 * 2026-08-09 的第一次内置尝试（PR #33）把骨骼画到
 * {@code FeatureRenderDispatcher#renderAllFeatures} 的世界阶段边界，并且
 * 用 {@code RenderSystem.getModelViewMatrixCopy() × bonePose} 当 ModelView。
 * 本移植已经多次实证：26.2 手部 pass 的 RenderSystem modelView 只是兼容残留，
 * 不能当坐标信源。再叠加 {@code visitBones} 的 skip 谓词把 GPU 骨骼整棵剪掉，
 * 结果是「日志说 GPU-baked 成功、画面上枪完全看不见」。
 *
 * <h2>这一版怎么改</h2>
 * <ul>
 *   <li>顶点保持骨骼本地空间，一次性上传为常驻 {@link GpuBuffer}；</li>
 *   <li>submit 时只收集 {@code poseStack.last().pose()} —— 这就是立方体
 *       consumer 路径已经在用的完整物品+骨骼矩阵，不再乘任何外部 modelView；</li>
 *   <li>DynamicTransforms = 该矩阵，shader 做 {@code Proj × ModelView × local}，
 *       与 consumer 路径 {@code Proj × I × (pose × local)} 等价；</li>
 *   <li>手部 / 世界两套登记表，只在对应的 {@code renderAllFeatures} 阶段边界绘制
 *       （手部由 {@link ScopeMaskRenderer#isInHandPass()} 判定）；</li>
 *   <li>管线显式声明深度/颜色状态，避免「不需要深度附件」被静默丢弃；</li>
 *   <li>用 vanilla {@code core/entity} + {@code NO_OVERLAY}/{@code EMISSIVE}，
 *       只绑 Sampler0，避开自定义 shader / 额外 sampler 的编译坑。</li>
 * </ul>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric 的 VBO 思路 (GPL-3.0)，
 * 按 26.2 GpuBuffer / RenderPass 重写。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    public static final int BAKED_LIGHT = 15728880;
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    // 配方对照 vanilla ENERGY_SWIRL：MATRICES_FOG_SNIPPET + core/entity +
    // EMISSIVE/NO_OVERLAY/NO_CARDINAL_LIGHTING，只声明 Sampler0。
    // 不用 ENTITY_SNIPPET，是因为它会带上 Sampler2（lightmap），而我们走
    // 满亮烘焙、不想再绑一张未声明用途的 lightmap。深度/颜色用 DEFAULT，
    // 这样能写入主 target 的深度附件（上一版漏声明深度会被静默丢弃）。
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity"))
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

    public record BakedBone(GpuBuffer vertexBuffer, int indexCount, Identifier texture) {
        public void close() {
            if (vertexBuffer != null) {
                RenderSystem.queueFencedTask(vertexBuffer::close);
            }
        }
    }

    public record DrawEntry(Matrix4f model, BakedBone bone) {
    }

    private static final List<DrawEntry> HAND_DRAWS = new ArrayList<>();
    private static final List<DrawEntry> WORLD_DRAWS = new ArrayList<>();
    private static boolean loggedFirstDraw = false;
    private static boolean gpuDisabledThisSession = false;

    private static final Map<Identifier, GpuTextureView> TEXTURE_VIEW_CACHE = new HashMap<>();
    private static Field textureViewField;

    public static boolean isGpuPathUsable() {
        return !gpuDisabledThisSession
                && MeshyConfig.GPU_BAKING.get()
                && !IrisCompat.isUsingRenderPack();
    }

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
            return new BakedBone(vertexBuffer, meshData.drawState().indexCount(), texture);
        } catch (Exception e) {
            GunMod.LOGGER.error("[TacZMeshLoader] Failed to bake bone geometry", e);
            return null;
        }
    }

    /**
     * @param bonePose submit 时 {@code poseStack.last().pose()} 的拷贝，
     *                 已经包含物品根变换 + 骨骼链，不要再乘 modelView。
     * @param handPass 当前 submit 是否发生在第一人称手部路径。
     */
    public static void submitBone(Matrix4f bonePose, BakedBone bone, boolean handPass) {
        if (bone == null) {
            return;
        }
        DrawEntry entry = new DrawEntry(new Matrix4f(bonePose), bone);
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
     */
    public static void renderAtPhaseBoundary() {
        boolean handPass = ScopeMaskRenderer.isInHandPass();
        List<DrawEntry> draws = handPass ? HAND_DRAWS : WORLD_DRAWS;
        if (draws.isEmpty()) {
            return;
        }
        try {
            drawList(draws);
        } catch (Exception e) {
            GunMod.LOGGER.error("[TacZMeshLoader] GPU mesh pass failed; falling back to consumer path for this session.", e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
        } finally {
            draws.clear();
        }
    }

    private static void drawList(List<DrawEntry> draws) {
        RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return;
        }
        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.getDepthTextureView();
        if (colorView == null || depthView == null) {
            return;
        }
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.bone().texture(), k -> new ArrayList<>()).add(entry);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_mesh_gpu",
                colorView,
                Optional.empty(),
                depthView,
                OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);

            for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
                GpuTextureView textureView = getTextureView(group.getKey());
                if (textureView == null) {
                    textureView = getTextureView(MissingTextureAtlasSprite.getLocation());
                }
                if (textureView == null) {
                    continue;
                }
                pass.bindTexture("Sampler0", textureView, sampler);

                for (DrawEntry entry : group.getValue()) {
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(entry.model(), WHITE));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer().slice());
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount()), indices.type());
                    pass.drawIndexed(entry.bone().indexCount(), 1, 0, 0, 0);
                }
            }
            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                long indices = 0;
                for (DrawEntry entry : draws) {
                    indices += entry.bone().indexCount();
                }
                GunMod.LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) on {} pass.",
                        draws.size(), indices, ScopeMaskRenderer.isInHandPass() ? "hand" : "world");
            }
        }
    }

    private PolyMeshGpuRenderer() {
    }
}
