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
import com.mojang.blaze3d.systems.ScissorState;
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
 * poly_mesh 的 GPU 静态烘焙渲染器 —— <b>仅第一人称手部 pass</b>（第 1 步：无光影；
 * 第 2 步 v2：光影下改画进 Iris 自己的手部 flush）。
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
 * <p>这次 GPU 表<b>只收第一人称手部 pass 当时登记的骨骼</b>，且只在手部 flush 里绘制。
 * 世界/GUI 全部走 collector。</p>
 *
 * <h2>绘制点：手部几何「当次 flush」之后，不是 renderLevel 末尾</h2>
 * <p>1.21.11 的手部几何不是延迟到世界渲染末尾统一 flush 的：{@code ItemInHandRenderer#renderHandsWithItems}
 * 自己就以 {@code featureRenderDispatcher.renderAllFeatures()} + {@code bufferSource.endBatch()}
 * 收尾（Iris 正是通过 {@code @WrapWithCondition}/{@code @WrapOperation} 这两个调用来接管手部绘制，
 * 见 {@code MixinItemInHandRenderer}）。字节码审计另见 {@code TML_GPU_STEP2_HANDFLUSH} §1：
 * {@code FeatureRenderDispatcher#renderAllFeatures} 里<b>根本没有</b> {@code RenderPass} 这个局部变量，
 * 它只是逐个调用各 feature renderer；每个批次真正的 {@code RenderPass} 在
 * {@code RenderType#draw(MeshData)} 内部创建（局部槽位 13），并按
 * {@code RenderSystem.outputColorTextureOverride / outputDepthTextureOverride} 解析输出目标。</p>
 *
 * <p>因此本仓把绘制点放在<b>本方法返回处</b>（= 那次 flush 的紧后，仍手在同一条栈上）：
 * {@code ItemInHandRendererMixin#tacz$drawMeshGpuAfterHandFeatureFlush}，
 * {@code @Inject(renderHandsWithItems, RETURN)}，{@code require=0}。</p>
 * <ul>
 *   <li>无光影：此处 ModelView / Projection 与原版刚用过的完全一致 —— 不再需要在 submit
 *       时刻偷拍 {@code Bᵀ}（第 1 步「相对人物世界位置恒定」bug 的根源，正是在
 *       {@code renderItemInHand} RETURN 现取已被还原的矩阵）。</li>
 *   <li>光影：Iris 用 {@code @WrapWithCondition}/​{@code @WrapOperation} 把上面那两个 flush
 *       调用换成它自己的 {@code HandRenderer#endRender()}，并且它是从
 *       {@code iris$renderHandsWithCustomRenderer} → <b>同一个</b> {@code renderHandsWithItems}
 *       进来的，所以同一个注入点天然落在 Iris 的 {@code HAND_SOLID} 阶段内：gbuffer 还绑着、
 *       投影是 Iris 的手部投影、ModelView 与刚 flush 完的手部几何同一个。在这里开自己的 pass，
 *       输出目标按原版 {@code RenderType#draw} 的同款规则解析（override 优先），因此常驻 VBO
 *       进得了 {@code gbuffers_hand}。<b>不需要 mixin Iris 内部类。</b></li>
 * </ul>
 *
 * <p>两条路共用<b>钩子存活证明</b>兜底：{@link #shouldSubmitGpu()} 只有在上一帧真的跑过
 * flush 钩子时才允许跳过 collector。映射漂移、mixin 没装上（{@code require=0} 静默失效）
 * → 下一帧自动回 collector，不会出现「collector 被跳过 + GPU 没画」的枪体消失。</p>
 *
 * <h2>管线配方</h2>
 * <p>底子用 {@code MATRICES_FOG_SNIPPET}（Globals + DynamicTransforms + Projection
 * + Fog），shader 用 vanilla {@code core/entity}：defines 取 {@code ALPHA_CUTOUT 0.1 +
 * NO_OVERLAY + NO_CARDINAL_LIGHTING}（顶点色直通 + lightmap 采样，与 collector 的
 * entityCutout 视觉差异只有 overlay）。lightmap 拿不到时退化 EMISSIVE 管线。</p>
 *
 * <p>光影下把这两条管线经 {@code IrisApi.assignPipeline(pipeline, IrisProgram.HAND)} 登记到
 * Iris 的 hand program（{@code ShaderKey.findBestMatch} 会因 {@code ALPHA_CUTOUT} +
 * {@code IrisVertexFormats.ENTITY} 命中 {@code HAND_CUTOUT}）。顶点格式必须与 pass 实际
 * 消费的一致：Iris 用 {@code MixinRenderPipeline#getVertexFormat} 把 {@code NEW_ENTITY} 替换成
 * 扩展实体格式，所以烘焙<b>按 {@code LIT_PIPELINE.getVertexFormat()} 当刻的返回值</b>写
 * （{@link #bakeFormat()}），格式换了立即重烘（{@link #getBakeFormat()} 由模型侧比对）。</p>
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
        /**
         * 烘进 buffer 的顶点格式（pass 消费端 {@code RenderPipeline#getVertexFormat()} 的当刻
         * 返回值）。光影激活时它是 Iris 的扩展实体格式，与 {@code NEW_ENTITY} 的 stride 不同，
         * 因此必须与绘制当刻一致，否则属性错位表现为模型拉伸/乱飞。
         */
        public final VertexFormat format;

        BakedBone(GpuBuffer vertexBuffer, int indexCount, VertexFormat format) {
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
            this.format = format;
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
    private static boolean loggedUnderShadersNoop = false;
    private static boolean loggedFormatMismatch = false;
    private static boolean lightmapUnavailable;
    /** 手部 pass 进行中（由 GameRendererMixin 在 renderItemInHand HEAD/RETURN 设置）。 */
    private static boolean inHandPass = false;
    /**
     * 最近一次「手部 flush 内绘制钩子」真正跑过的帧号 —— submit 侧的<b>存活证明</b>。
     *
     * <p>GPU 路径会<u>跳过</u> collector 提交，所以一旦绘制钩子没跑（Iris 换了内部结构、
     * mixin 因 {@code require=0} 静默失效），枪体就会整个消失。这里记录钩子最后一次真正
     * 执行的帧，{@link #shouldSubmitGpu()} 只允许在「上一帧刚跑过」时走 GPU；钩子失联
     * 立刻回到 collector（最坏情况丢一帧 GPU 加速，不会丢枪）。</p>
     */
    private static int lastHandFlushFrame = Integer.MIN_VALUE;
    private static int frameId = 0;
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
     *   <li><b>有配对的 flush 绘制点</b>（{@link #lastHandFlushFrame} 是本帧或上一帧）；</li>
     *   <li>光影下额外要求 {@code MeshGpuUnderShaders} 打开且当前在 Iris 的
     *       {@code HAND_SOLID} 阶段里（见 {@link #isGpuPathUsable()}）；</li>
     *   <li><b>无光影时现在就在 vanilla 手部 pass 里</b>——而不是
     *       {@code transformType.firstPerson()}。后者对「用第一人称上下文画 GUI」
     *       这类路径也会为 true，正是关 PR WORLD_DRAWS 泄漏的入口。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpu() {
        if (!isGpuPathUsable()) {
            return false;
        }
        if (!handFlushAlive()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack()) {
            // 光影下 submit 发生在 Iris 自己的手部阶段内（vanilla 的 renderItemInHand 被
            // Iris 的 @Redirect 掏空，里面不会有 submit），所以门禁问 Iris 要阶段状态。
            return IrisCompat.isRenderingSolidHandPass();
        }
        return inHandPass;
    }

    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack()) {
            // 第 2 步 v2：光影下的 GPU 路径只在「绘制发生在 Iris 自己那次手部 flush 之内」
            // 时成立（见类注释）。这一步是实验性的，默认关；且必须 Iris 版本已审计 +
            // 钩子存活证明通过（shouldSubmitGpu 里查），三条缺一不可。
            if (!MeshyConfig.GPU_UNDER_SHADERS.get()) {
                return false;
            }
            if (!IrisCompat.supportsHandFlushHook()) {
                if (!loggedUnderShadersNoop) {
                    loggedUnderShadersNoop = true;
                    LOGGER.warn("[TacZMeshLoader] MeshGpuUnderShaders=true needs the audited Iris hand-flush"
                            + " hook (Iris 1.10.x); keeping the collector path.");
                }
                return false;
            }
            return true;
        }
        return true;
    }

    /** 本帧或上一帧刚跑过手部 flush 绘制钩子 —— 说明这次 GPU submit 有配对的绘制点。 */
    private static boolean handFlushAlive() {
        return lastHandFlushFrame == frameId || lastHandFlushFrame == frameId - 1;
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

    /**
     * 本次烘焙<b>必须</b>使用的顶点格式：直接问绘制端（{@code LIT_PIPELINE}）当刻的格式。
     *
     * <p>光影激活时 Iris 的 {@code MixinRenderPipeline#iris$change} 会把 {@code NEW_ENTITY}
     * 替换成它的扩展实体格式（多 at_midBlock / at_tangent / at_midUV，stride 也不同）。
     * pass 消费端读的就是替换后的格式，所以烘焙端必须问同一个 getter，而不能写死
     * {@code DefaultVertexFormat.NEW_ENTITY}，否则就是「拉伸的枪模」。无光影时该 getter
     * 原样返回 {@code NEW_ENTITY}。</p>
     */
    public static VertexFormat bakeFormat() {
        VertexFormat format = LIT_PIPELINE.getVertexFormat();
        return format != null ? format : DefaultVertexFormat.NEW_ENTITY;
    }

    public static BakedBone bakeBone(List<PolyMesh> meshes, int lightKey, VertexFormat format) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        // NEW_ENTITY stride 36；Iris 扩展格式更宽。按 48 预留可覆盖两者，避免 grow。
        long capacity = vertexCount * 48L + 1024L;
        ByteBufferBuilder scratch = new ByteBufferBuilder((int) Math.min(capacity, Integer.MAX_VALUE));
        BufferBuilder builder = new BufferBuilder(scratch, VertexFormat.Mode.QUADS, format);
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
            return new BakedBone(vertexBuffer, meshData.drawState().indexCount(), format);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to bake bone geometry", e);
            return null;
        } finally {
            scratch.close();
        }
    }

    /**
     * 登记一根骨骼的本帧绘制。<b>只允许在手部 pass / Iris 手部阶段内调用</b>
     * （由 {@link #shouldSubmitGpu()} 把门）。
     *
     * <p>这里只存「骨骼矩阵 + 纹理 + 常驻 VBO」，不做任何顶点变换：模型矩阵在
     * {@link #drawList} 里乘上 flush 当刻的 ModelView（与原版刚 flush 的那批手部几何
     * 用的是同一份矩阵），因此与 collector 路径逐帧等价。</p>
     */
    public static void submitBone(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        HAND_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /** 挂在 {@code GameRenderer#render} HEAD（每帧一次、早于 FOV/手部 submit）。 */
    public static void beginFrame() {
        frameId++;
        boolean shaders = IrisCompat.isUsingRenderPack();
        if (shaders != lastShaderPackState) {
            lastShaderPackState = shaders;
            bakeGeneration++;
            LOGGER.info("[TacZMeshLoader] Shader pack state changed (active={}); mesh bake generation -> {}",
                    shaders, bakeGeneration);
        }
        HAND_DRAWS.clear();
    }

    /** 当前烘焙世代号。烘焙缓存持有者在 submit 时比对，不匹配须立即重烘。 */
    public static int getBakeGeneration() {
        return bakeGeneration;
    }

    /**
     * 手部 flush 的绘制钩子，由 {@code ItemInHandRendererMixin#tacz$drawMeshGpuAfterHandFeatureFlush}
     * 在 {@code renderHandsWithItems} 的收尾 flush 之后调用。
     *
     * <p><b>一个注入点覆盖两条路</b>：无光影时「那次 flush」就是原版自己写在
     * {@code renderHandsWithItems} 末尾的 {@code renderAllFeatures()} + {@code endBatch()}；
     * 光影时 Iris 用 {@code @WrapOperation} 把这两个调用换成它自己的
     * {@code HandRenderer#endRender()}，而 Iris 本身也是从 {@code iris$renderHandsWithCustomRenderer}
     * → 同一个 {@code renderHandsWithItems} 进来的。所以本方法返回的那一刻，两种情形都
     * 「刚画完手部几何、仍在手部 pass 的栈上」：无光影 —— {@code GameRenderer#renderItemInHand}
     * 还没还原 ModelView；光影 —— 仍在 Iris 的 {@code HAND_SOLID} 阶段内、gbuffer 还绑着。</p>
     *
     * <p>无论是否真的画了，都先记 {@link #lastHandFlushFrame}（submit 侧的存活证明），
     * 末尾一律清空当帧清单。</p>
     */
    public static void renderAtHandFlush() {
        lastHandFlushFrame = frameId;
        if (HAND_DRAWS.isEmpty()) {
            // 绝大多数帧走这里（没持 mesh 枪 / 光影未开实验开关）。存活证明已经记下了，
            // 其余一律不查：IrisCompat.isUsingRenderPack() 是反射桥，别在热路径上白调。
            return;
        }
        if (!isGpuPathUsable()) {
            HAND_DRAWS.clear();
            return;
        }
        boolean irisFlush = IrisCompat.isUsingRenderPack();
        try {
            drawList(HAND_DRAWS, irisFlush);
        } catch (Exception | LinkageError e) {
            // LinkageError：光影下这条路径依赖 Iris 的 flush 时机，方法缺失也要能自愈回 collector。
            LOGGER.error("[TacZMeshLoader] GPU mesh hand flush failed (irisFlush={}); "
                    + "falling back to collector path for this session.", irisFlush, e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
        } finally {
            HAND_DRAWS.clear();
        }
    }

    private static void drawList(List<DrawEntry> draws, boolean irisFlush) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
        if (mainTarget == null) {
            return;
        }

        // 输出目标的选择与 vanilla RenderType#draw 逐条同款（1.21.11 字节码审计，见
        // docs/TML_GPU_STEP2_HANDFLUSH_20260831.md §1）：override 优先，且只有
        // RenderTarget.useDepth 为真时才挂深度附着。原版刚刚 flush 的那批手部几何用的
        // 就是这两个值 —— 跟着它走，无光影时落进主渲染目标，光影时落进 Iris 当刻绑定的
        // gbuffer：Iris 1.10.x 的 MixinGlCommandEncoder 用 @Redirect 拦掉了
        // createRenderPass 里的 glBindFramebuffer（条件 ImmediateState.safeToMultiply /
        // 阴影 pass），并在 trySetup 里只把「非 ExtendedShader」的 pass 复位回原版 FBO，
        // 因此在世界渲染阶段内新建的 pass 会留在 Iris 绑定的 framebuffer 上。
        GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride
                        : mainTarget.getDepthTextureView())
                : null;
        if (colorView == null) {
            return;
        }

        GpuTextureView lightmapView = resolveLightmap(mc);
        boolean lit = lightmapView != null;
        RenderPipeline pipeline = lit ? LIT_PIPELINE : EMISSIVE_PIPELINE;
        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        if (irisFlush) {
            // 自建管线不在 Iris 的 coreShaderMap 里，默认只能拿原版程序（= 无光影光照）。
            // 登记进 HAND program 后 Iris 会按光影包的 gbuffers_hand 为这条管线编译程序，
            // 与 collector 路径下 entityCutout 得到的照明一致。两条管线都登记：lightmap
            // 视图何时不可用是运行期才决定的，届时不能才发现 EMISSIVE 没分配。
            // 幂等且失败无害（IrisCompat 内部缓存 + 吞异常）。
            IrisCompat.assignMeshPipelineToHand(pipeline);
        }

        // 烘焙格式必须与 pass 消费端的格式一致：Iris 激活时 pipeline.getVertexFormat()
        // 会被换成扩展实体格式。不一致就跳过本次绘制并 bump 世代号，让模型在下一次
        // submit 立即重烘（宁少一帧，也不按错 stride 解读 buffer）。
        VertexFormat passFormat = pipeline.getVertexFormat();
        if (passFormat == null) {
            passFormat = DefaultVertexFormat.NEW_ENTITY;
        }
        List<DrawEntry> drawable = new ArrayList<>(draws.size());
        boolean formatChanged = false;
        for (DrawEntry entry : draws) {
            if (entry.bone().format == passFormat) {
                drawable.add(entry);
            } else {
                formatChanged = true;
            }
        }
        if (formatChanged) {
            // 世代号 +1：所有持缓存的模型下一次 submit 立即重烘，不再走 1 秒光照节流。
            bakeGeneration++;
            if (!loggedFormatMismatch) {
                loggedFormatMismatch = true;
                LOGGER.warn("[TacZMeshLoader] Mesh bake vertex format no longer matches the pipeline's"
                        + " (pass={}); re-baking next frame.", passFormat);
            }
        }
        if (drawable.isEmpty()) {
            return;
        }

        // ModelViewMat：直接取 flush 当刻的 getModelViewMatrix()。绘制点就在原版/ Iris
        // 那次手部 flush 的紧后，两份矩阵是同一个值 —— 这正是 collector 路径烘进顶点的
        // pose 稍前所乘的那份，所以「GPU 顶点留骨骼本地 + mv = MV × pose」与 collector
        // 「pose 烘进顶点 + MV 原样」逐帧等价。（第 1 步曾在 renderItemInHand RETURN
        // 现取已被还原的栈，才有「相对人物世界位置恒定」老 bug；现在不存在这个时刻差。）
        Matrix4f handMv = new Matrix4f(RenderSystem.getModelViewMatrix());

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : drawable) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        // 1.21.11 关键差异：DynamicUniforms.writeTransform 会 map DynamicTransforms UBO
        // （GpuBuffer.mapBuffer），而 open render pass 期间禁止任何 map 指令 —— 26.2 允许
        // 在 pass 内写、1.21.11 直接抛 "Close the existing render pass before performing
        // additional commands"。所以所有骨骼变换必须在开 pass 之前写进 UBO、拿到 slice。
        Map<DrawEntry, GpuBufferSlice> transformByEntry = new IdentityHashMap<>();
        int maxIndexCount = 0;
        for (DrawEntry entry : drawable) {
            // ModelViewMat = 手部 MV × pose_submit（乘序同 vanilla：顶点先套 pose 再进相机系）。
            Matrix4f mv = new Matrix4f(handMv).mul(entry.model());
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
        // 这里不在任何 render pass 内（原版每个批次自己 createRenderPass + close），
        // createRenderPass 的断言安全。
        // 颜色 OptionalInt.empty() = 不清屏，深度 OptionalDouble.empty() = 不清深度。
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_mesh_gpu",
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            // 与 vanilla RenderType#draw 一致：手部几何若带 scissor，GPU 批次必须同样裁剪，
            // 否则 GUI/PIP 留下的 scissor 状态会让枪被切掉一块。
            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.enabled()) {
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
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
                long indexTotal = 0;
                for (DrawEntry entry : drawable) {
                    indexTotal += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) in {} hand flush:"
                                + " lit={}, colorView={}, depthView={}, vertexFormat={}",
                        drawable.size(), indexTotal, irisFlush ? "Iris" : "vanilla", lit,
                        System.identityHashCode(colorView), System.identityHashCode(depthView),
                        passFormat);
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
