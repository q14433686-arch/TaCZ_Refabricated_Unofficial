package cn.sh1rocu.tacz.compat.meshloader.render;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.DepthTestFunction;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * poly_mesh 的 GPU 静态烘焙渲染器 —— <b>仅第一人称手部 pass、无光影</b>（第 1 步）。
 *
 * <h2>为什么前几次内置失败、这次怎么改</h2>
 * <p>顶点留在骨骼本地坐标、绘制时把骨骼矩阵写进 DynamicTransforms.ModelViewMat，
 * 与 collector 路径「把 pose 烘进顶点、identity 交给 collector」在数学上等价。</p>
 *
 * <p>关 PR（#33/#69/#70/#71）的两条硬伤不在这套代数上：</p>
 * <ol>
 *   <li><b>全局 WORLD_DRAWS 表</b>：GUI / 掉落物 / 展示框 / 第三人称的 submit
 *       也被登记，然后在<b>世界</b>那次 feature flush 用世界投影画出去——
 *       这就是「帖图不对」。</li>
 *   <li><b>弹匣</b>：没接 {@code IMirrorGeometry}，纯 mesh 弹匣在主路径里被漏画。</li>
 * </ol>
 *
 * <p>这次 GPU 表<b>只收第一人称手部 pass 当时登记的骨骼</b>，且只在手部 pass
 * （vanilla {@code renderItemInHand}）的 RETURN 点绘制。世界/GUI 全部走 collector。</p>
 *
 * <h2>1.21.11 与 26.2 的注入点差异</h2>
 * <p>26.2 在 {@code renderAllFeatures} 的 {@code executeSolid} 之后画。1.21.11 没有
 * executeSolid/executeTranslucent 拆分 —— 手部几何经 {@code SubmitNodeStorage} 延迟
 * 到 {@code renderLevel} 末尾那次 {@code renderAllFeatures()} 统一 flush。因此本仓
 * 把绘制点放在 {@code renderItemInHand} 的 RETURN（既有 {@code GameRendererMixin}
 * {@code tacz$endHandPass} 处）：那时手部投影已设好、深度已被
 * {@code clearDepthTexture} 清空，而手部立方体还没 flush。全部是不透明几何，
 * 深度缓冲保证最终遮挡关系与「先立方体后 mesh」完全一致；translucent 骨骼留在
 * collector（较后 flush），仍叠在 GPU 枪体之上。</p>
 *
 * <h2>管线配方</h2>
 * <p>底子用 {@code MATRICES_FOG_SNIPPET}（Globals + DynamicTransforms + Projection
 * + Fog），shader 用 vanilla {@code core/entity}：defines 取 {@code ALPHA_CUTOUT 0.1 +
 * NO_OVERLAY + NO_CARDINAL_LIGHTING}（顶点色直通 + lightmap 采样，与 collector 的
 * entityCutout 视觉差异只有 overlay）。lightmap 拿不到时退化 EMISSIVE 管线。</p>
 *
 * <p>第 2 步（光影下走 vanilla RenderType 管道 / 强制自定义 pass）不在本轮。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)；GPU 路径按 26.2
 * {@code 8191f6b}/{@code 0ea0fb6}/{@code 9f7412e} 机械移植到 1.21.11 改名映射。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TacZMeshLoader");

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO_OFFSET = new Vector3f();
    private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
    private static final int LIGHT_GRID = 4;

    /** 有 lightmap 采样（Sampler2）的 lit 管线；拿不到 lightmap 时用 EMISSIVE。 */
    private static final RenderPipeline LIT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity"))
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler0")
                    .withSampler("Sampler2")
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withColorWrite(true)
                    .build());

    private static final RenderPipeline EMISSIVE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity_emissive"))
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler0")
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withColorWrite(true)
                    .build());

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
    private static boolean gpuDisabledThisSession = false;
    private static boolean lightmapUnavailable;
    /** 本帧已画过（vanilla 每帧一次手部 pass；保留此闸门以兼容 Iris 二次 pass 的未来接入）。 */
    private static boolean drawnThisFrame = false;
    /** 手部 pass 进行中（由 GameRendererMixin 在 renderItemInHand HEAD/RETURN 设置）。 */
    private static boolean inHandPass = false;
    /**
     * 烘焙世代号：光影包开关每翻转一次 +1（{@link #beginFrame} 逐帧检测）。
     *
     * <p>烘焙产物依赖当时的光影状态——Iris 激活时会扩展实体顶点格式（附加属性、
     * stride 变化），切换光影后用新管线按新 stride 解读旧 buffer，属性错位表现为
     * <b>模型拉伸</b>。持有烘焙缓存的模型在 submit 时比对世代号，不匹配立即重烘。</p>
     */
    private static int bakeGeneration = 0;
    private static boolean lastShaderPackState = false;

    private PolyMeshGpuRenderer() {
    }

    /**
     * 当前这次 submit 是否该走 GPU。必须同时满足：
     * <ul>
     *   <li>配置打开且本会话未因异常关闭；</li>
     *   <li><b>无光影</b>（第 2 步之前，光影下保持 collector 回退）；</li>
     *   <li><b>现在就在 vanilla 手部 pass 里</b>——而不是
     *       {@code transformType.firstPerson()}。后者对「用第一人称上下文画 GUI」
     *       这类路径也会为 true，正是关 PR WORLD_DRAWS 泄漏的入口。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpu() {
        if (!isGpuPathUsable()) {
            return false;
        }
        return inHandPass;
    }

    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        // 第 1 步只有自定义 pass 一种画法。无光影时直接可用；光影下默认回退
        // collector（第 2 步才落地 vanilla RenderType 管道变体，让 Iris 给枪体
        // 套光影光照）。MeshGpuUnderShaders 是实验强开：光影下仍走自定义 pass，
        // 枪体<b>无光影光照</b>（与 26.2 的对照组语义一致，仅作排查用）。
        return !IrisCompat.isUsingRenderPack() || MeshyConfig.GPU_UNDER_SHADERS.get();
    }

    /** 由 GameRendererMixin 在 renderItemInHand HEAD/RETURN 调用。 */
    public static void setInHandPass(boolean value) {
        inHandPass = value;
    }

    public static boolean isInHandPass() {
        return inHandPass;
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
        return LightTexture.pack(qb, qs);
    }

    public static BakedBone bakeBone(List<PolyMesh> meshes, int lightKey) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        // NEW_ENTITY 格式 stride 36 字节，预留些余量避免 grow。
        long capacity = vertexCount * 48L + 1024L;
        ByteBufferBuilder scratch = new ByteBufferBuilder((int) Math.min(capacity, Integer.MAX_VALUE));
        BufferBuilder builder = new BufferBuilder(scratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
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

    /** 挂在 {@code GameRenderer#render} HEAD（每帧一次、早于 FOV/手部 submit）。 */
    public static void beginFrame() {
        boolean shaders = IrisCompat.isUsingRenderPack();
        if (shaders != lastShaderPackState) {
            lastShaderPackState = shaders;
            bakeGeneration++;
            LOGGER.info("[TacZMeshLoader] Shader pack state changed (active={}); mesh bake generation -> {}",
                    shaders, bakeGeneration);
        }
        HAND_DRAWS.clear();
        drawnThisFrame = false;
    }

    /** 当前烘焙世代号。烘焙缓存持有者在 submit 时比对，不匹配须立即重烘。 */
    public static int getBakeGeneration() {
        return bakeGeneration;
    }

    /**
     * 在手部 pass 的 RETURN 点绘制（{@code renderItemInHand} 之后、手部 flush 之前）。
     *
     * <p>不画的情况：不在手部 pass、本帧已画过、清单为空、或本会话 GPU 已降级。
     * 无论成败，末尾都清空当帧清单。</p>
     */
    public static void renderAfterSolid() {
        if (!inHandPass) {
            HAND_DRAWS.clear();
            return;
        }
        if (HAND_DRAWS.isEmpty()) {
            return;
        }
        if (drawnThisFrame) {
            // Iris 第二次手部 pass 的重复 submit：跳过（第 2 步接入时生效）。
            HAND_DRAWS.clear();
            return;
        }
        try {
            drawList(HAND_DRAWS);
            drawnThisFrame = true;
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
        RenderTarget mainTarget = mc.getMainRenderTarget();
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

        // 手部立方体尚未 flush，但 MV 与稍后 collector flush 时一致（renderItemInHand
        // 已在内部 push/pop 自己的 camera matrix，RETURN 后 MV 回到 renderAllFeatures
        // 将用的同一份）。取一次全体通用，每骨骼再乘 pose。
        Matrix4f handModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        // 1.21.11 关键差异：DynamicUniforms.writeTransform 会 map DynamicTransforms UBO
        // （GpuBuffer.mapBuffer），而 open render pass 期间禁止任何 map 指令 —— 26.2 允许
        // 在 pass 内写、1.21.11 直接抛 "Close the existing render pass before performing
        // additional commands"。所以所有骨骼变换必须在开 pass 之前写进 UBO、拿到 slice。
        Map<DrawEntry, GpuBufferSlice> transformByEntry = new IdentityHashMap<>();
        int maxIndexCount = 0;
        for (DrawEntry entry : draws) {
            Matrix4f mv = new Matrix4f(handModelView).mul(entry.model());
            transformByEntry.put(entry, RenderSystem.getDynamicUniforms().writeTransform(
                    mv, WHITE, ZERO_OFFSET, IDENTITY_TEXTURE_MATRIX));
            maxIndexCount = Math.max(maxIndexCount, entry.bone().indexCount);
        }

        // 顺序索引缓冲也是懒分配：getBuffer(n) 在首次/扩容时会 map+写索引，同样必须在
        // pass 外先触发一次（预热到本帧最大 indexCount），进 pass 后 getBuffer 只返回
        // 既有 GpuBuffer、不再 map。
        RenderSystem.AutoStorageIndexBuffer indices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        indices.getBuffer(maxIndexCount);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // renderItemInHand RETURN 不在任何 render pass 内，createRenderPass 断言安全。
        // 颜色 OptionalInt.empty() = 不清屏，深度 OptionalDouble.empty() = 不清深度。
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_mesh_gpu",
                colorView,
                OptionalInt.empty(),
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
                    pass.setUniform("DynamicTransforms", transformByEntry.get(entry));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                    // 1.21.11 drawIndexed(baseVertex, firstIndex, count, instanceCount)：
                    // 顺序索引缓冲 0..count-1，故 baseVertex=0、firstIndex=0、单实例。
                    pass.drawIndexed(0, 0, entry.bone().indexCount, 1);
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
            GpuTextureView view = mc.gameRenderer.lightTexture().getTextureView();
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
