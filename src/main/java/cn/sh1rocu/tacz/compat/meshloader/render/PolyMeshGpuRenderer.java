package cn.sh1rocu.tacz.compat.meshloader.render;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
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
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
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
 * poly_mesh 的 GPU 静态烘焙渲染器。
 *
 * <ul>
 *   <li><b>第 1 步</b>：无光影第一人称手部；</li>
 *   <li><b>第 2 步 v2</b>：光影下把手部 pass 改画进 Iris 自己的那次手部 flush
 *       （{@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md}）；</li>
 *   <li><b>第 3 步</b>：世界语境（第三人称手持 / 掉落物 / 展示框 / 展示台雕像）——
 *       {@link #WORLD_DRAWS} 表 + {@link #renderAtWorldFlush()}，消费点是
 *       {@code FeatureRenderDispatcher#renderAllFeatures} 的返回处（见本文
 *       「世界绘制点」一节与 {@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md} §4）。</li>
 * </ul>
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
 * <p>所以硬伤从来不是「有一张世界表」，而是<b>提交侧没有语境闸门</b> + <b>绘制时矩阵取自
 * 错误的时刻</b>。第 3 步的世界表因此把闸门全放在 submit 上
 * （{@link #shouldSubmitGpuWorld}：GUI / Screen 内嵌预览 / 镜内那遍 / 阴影 / 手部 pass
 * 逐个拒收），绘制侧则与手部同构：只在「刚 flush 完本帧几何、MV 还是这批几何将要用的那一份」
 * 的时刻画。GUI 语境一律不进 GPU（正交投影的 pose 与世界的不是同一套代数）。</p>
 *
 * <h2>绘制点：手部几何「当次 flush」之后，不是 renderLevel 末尾</h2>
 * <p>1.21.11 的手部几何不是延迟到世界渲染末尾统一 flush 的：{@code ItemInHandRenderer#renderHandsWithItems}
 * 自己就以 {@code featureRenderDispatcher.renderAllFeatures()} + {@code bufferSource.endBatch()}
 * 收尾（Iris 正是通过 {@code @WrapWithCondition}/{@code @WrapOperation} 这两个调用来接管手部绘制，
 * 见 {@code MixinItemInHandRenderer}）。CI 上的 1.21.11 字节码核实：该方法共 143 行、只有
 * <b>1 个 return</b>，那两个 flush 调用就是倒数第二/最后一条指令（{@code TML_GPU_STEP2_HANDFLUSH} §3）。
 * 同一次审计的另一条：
 * {@code FeatureRenderDispatcher#renderAllFeatures} 里<b>根本没有</b> {@code RenderPass} 这个局部变量，
 * 它只是逐个调用各 feature renderer；每个批次真正的 {@code RenderPass} 在
 * {@code RenderType#draw(MeshData)} 内部创建（局部槽位 13），并按
 * {@code RenderSystem.outputColorTextureOverride / outputDepthTextureOverride} 解析输出目标。</p>
 *
 * <p>因此本仓把绘制点放在<b>本方法返回处</b>（= 那次 flush 的紧后，仍在同一条栈上）：
 * {@code ItemInHandRendererMixin#tacz$drawMeshGpuAfterHandFeatureFlush}，
 * {@code @Inject(renderHandsWithItems, RETURN)}，{@code require=0}。</p>
 * <ul>
 *   <li>无光影：此处 ModelView / Projection 与原版刚用过的完全一致 —— 不再需要在 submit
 *       时刻偷拍 {@code Bᵀ}（第 1 步「相对人物世界位置恒定」bug 的根源，正是在
 *       {@code renderItemInHand} RETURN 现取已被还原的矩阵）。</li>
 *   <li>光影：Iris 用 {@code @WrapWithCondition}/{@code @WrapOperation} 把上面那两个 flush
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
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                    // 26.1.2（frame-graph/RenderState 纪元）：Builder 没有
                    // withDepthTestFunction/withDepthWrite/withColorWrite，深度配置走聚合形态
                    // DepthStencilState(CompareOp, writeDepth)，颜色写掩码走 ColorTargetState。
                    // LESS_THAN_OR_EQUAL ≙ 1.21.11 的 DepthTestFunction.LEQUAL_DEPTH_TEST；
                    // WRITE_COLOR ≙ withColorWrite(true)；blend 保持空 = withColorWrite(true) 的无混合全写。
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                    .withColorTargetState(new ColorTargetState(java.util.Optional.empty(),
                            ColorTargetState.WRITE_COLOR))
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
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                    // 26.1.2（frame-graph/RenderState 纪元）：Builder 没有
                    // withDepthTestFunction/withDepthWrite/withColorWrite，深度配置走聚合形态
                    // DepthStencilState(CompareOp, writeDepth)，颜色写掩码走 ColorTargetState。
                    // LESS_THAN_OR_EQUAL ≙ 1.21.11 的 DepthTestFunction.LEQUAL_DEPTH_TEST；
                    // WRITE_COLOR ≙ withColorWrite(true)；blend 保持空 = withColorWrite(true) 的无混合全写。
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                    .withColorTargetState(new ColorTargetState(java.util.Optional.empty(),
                            ColorTargetState.WRITE_COLOR))
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

    /**
     * 世界语境（第三人称手持 / 掉落物 / 展示框 / 展示台雕像）的登记表。
     *
     * <p>与手部表同构，但消费点是<b>世界那一次</b> {@code FeatureRenderDispatcher#renderAllFeatures}
     * 的返回处（{@code ItemInHandRendererMixin} 那次是手部，两表互不消费）。防泄漏全部在
     * <b>提交侧</b>（{@link #shouldSubmitGpuWorld}）：GUI / Screen 内嵌预览 / 镜内那遍 /
     * 阴影 pass / 手部 pass 的 submit 进不了这张表，消费侧只负责「在正确的 pass 用正确的
     * ModelView 画正确的表」。</p>
     *
     * <p>为什么绘制点选在 {@code renderAllFeatures} 返回处：1.21.11 的
     * {@code LevelRenderer} 主通道里那一段是（CI javap 实测，见 docs/TML_GPU_STEP2_HANDFLUSH
     * §4）{@code popPush("renderFeatures") -> renderAllFeatures() -> bufferSource.endLastBatch()}，
     * 即地形深度已就绪、本帧立方体/实体几何还压在 builder 里等 {@code endLastBatch}；
     * 此刻 {@code RenderSystem.getModelViewMatrix()} 正是那些批次待会儿在 {@code RenderType#draw}
     * 里要写进 {@code DynamicTransforms.ModelViewMat} 的<b>同一个值</b>（1.21.11 的
     * {@code RenderType#draw} 就是在 draw 当刻现取 {@code getModelViewMatrix()}）。
     * 「GPU 骨骼用 flush 当刻的 MV + submit 当刻的骨骼 pose」与 collector
     * 「pose 烘进顶点 + flush 当刻 MV」因此逐帧等价 —— 这正是隔壁 26.2 分支踩的
     * 「相对视角固定」坑的解法：MV 不能取自别的时刻。</p>
     */
    private static final List<DrawEntry> WORLD_DRAWS = new ArrayList<>();

    /**
     * 延迟释放池：世界 LRU 缓存被逐出/作废的 VBO 先进这里，下一帧 {@link #beginFrame} 才 close。
     *
     * <p>不能当场 close：同帧内两个实体共享同一模型实例，甲 submit 已把某光照档的 VBO 登记进
     * {@link #WORLD_DRAWS}，乙随后触发 LRU 逐出同一档 —— 当场 close 等于让本帧帧末引用已销毁
     * 的 buffer。绘制永远发生在本帧提交之后、下一帧 {@code beginFrame} 之前，所以「下一帧再关」
     * 是最小充分延迟。</p>
     */
    private static final List<BakedBone> DEFERRED_RELEASE = new ArrayList<>();

    private static boolean loggedFirstDraw = false;
    private static boolean loggedFirstWorldDraw = false;
    private static boolean gpuDisabledThisSession = false;
    private static boolean loggedUnderShadersNoop = false;
    private static boolean loggedFormatMismatch = false;
    /** 只是日志去重：以前是一次性闩锁，会把一次瞬时取空变成整会话 EMISSIVE（已改掉，见 resolveLightmap）。 */
    private static boolean loggedLightmapFailure;
    /** {@link #gpuMasterUsable()} 因「光影 + 取不到 lightmap」拒收时的去重标志（每次拒收只报一行）。 */
    private static boolean loggedLightmapRefusal;
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
    /** 最近一次「世界 flush 绘制钩子」真正跑过的帧号（{@link #WORLD_DRAWS} 的存活证明）。 */
    private static int lastWorldFlushFrame = Integer.MIN_VALUE;
    /** 本帧是否已经消费（画 + 清）过世界表。镜内那一遍画但不清，所以不能复用存活证明。 */
    private static int worldConsumedFrame = Integer.MIN_VALUE;
    private static int bakesThisFrame = 0;
    /**
     * 世界钩子连续失败计数：达到阈值只关<b>世界</b>路径，手部那条不受牵连
     * （世界 pass 的环境比手部复杂，且镜内/always-on-top 这类次级 pass 可能正卡在
     * 别的 render pass 里 —— 那种情况下反复抛异常比回 collector 慢得多，也更吵）。
     */
    private static int worldConsecutiveFailures = 0;
    private static boolean gpuWorldDisabledThisSession = false;
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

    /**
     * 当前这次<b>世界语境</b> submit 是否该走 GPU（登记进 {@link #WORLD_DRAWS}）。
     *
     * <p>提交侧闸门 —— 关 PR 世界表泄漏的每个入口都在这里逐个封死：</p>
     * <ul>
     *   <li>{@code MeshGpuWorld} 打开、GPU 路径本会话可用、<b>世界</b> flush 钩子存活
     *       （{@link #worldFlushAlive()}，与手部同一手法：钩子失联就回 collector，
     *       最坏是这一帧没吃到 GPU，绝不会「整把枪消失」）；</li>
     *   <li><b>不在</b>手部 pass —— 手部有自己的表与自己的 flush 时刻；</li>
     *   <li><b>不在</b> Screen 提取窗口 —— {@link ScreenRenderTracker} 框住 {@code Screen}
     *       的 extract 阶段，背包人偶 / 枪匠桌预览这类 GUI 内嵌 3D 的 submit 全在里面；
     *       它们的 pose 带 GUI 投影，落进世界表就是「枪画进世界」事故。
     *       刻意<b>不用</b> {@code RenderDistance.isGuiRender()} 那种 100ms 时间戳窗口
     *       （开着菜单时世界提取也会命中 ⇒ 一开背包全场景 mesh 枪跌回 collector）；</li>
     *   <li><b>不在</b>镜内那一遍（PIP 二次渲染）—— 防御性：提交只在世界提取阶段发生一次，
     *       镜内那遍若真有 mod 补提交，pose 语义未知，宁可拒收（消费侧对镜内的处理见
     *       {@link #renderAtWorldFlush}）；</li>
     *   <li><b>不在</b>阴影 pass —— Iris 阴影遍的投影/MV 是太阳视角，登记进主视角的表必画错；</li>
     *   <li>光影下额外要求 {@code MeshGpuWorldUnderShaders}（R3 起默认开，见 {@link #worldGpuAllowed}）。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpuWorld() {
        if (gpuWorldDisabledThisSession || !MeshyConfig.GPU_WORLD.get() || !gpuMasterUsable()) {
            return false;
        }
        if (!worldFlushAlive()) {
            return false;
        }
        if (inHandPass) {
            return false;
        }
        if (ScreenRenderTracker.isRenderingScreen()) {
            return false;
        }
        if (isInsideScopeLevelRender()) {
            return false;
        }
        if (IrisCompat.isRenderShadow()) {
            return false;
        }
        return worldGpuAllowed();
    }

    /**
     * 光影下的世界 GPU 路径是否放行。
     *
     * <p>世界那一次 flush 里，Iris 对 {@code ENTITY_CUTOUT} 那条 vanilla 管线的默认接管
     * （{@code gbuffers_entities}）正是我们想要的照明；但本仓的 GPU 走的是<b>自建管线</b>
     * {@code tacz:pipeline/mesh_entity}，它不在 Iris 的 coreShaderMap 里 ⇒ 不登记就只能拿
     * 原版程序（无光影照明）。登记用的常量已用 CI javap 核实为 {@code IrisProgram.ENTITIES}
     * （见 {@code IrisCompat#assignMeshPipelineToEntity}；{@code EMISSIVE_ENTITIES} 不可拿来当
     * 「全亮」用，那条枚举值服务的是别的语义）。这条组合已于 2026-08-31 实机 PASS，
     * R3 起默认开；当时的顾虑只是「签名未知」这一项，而它早已审掉。</p>
     */
    private static boolean worldGpuAllowed() {
        if (!IrisCompat.isUsingRenderPack()) {
            return true;
        }
        if (!MeshyConfig.GPU_WORLD_UNDER_SHADERS.get()) {
            return false;
        }
        return IrisCompat.supportsHandFlushHook();
    }

    /**
     * 诊断用：世界语境 submit 被拒的<b>第一条</b>原因；门闸全放行时返回 null。
     *
     * <p>{@link #shouldSubmitGpuWorld()} 的语义是「静默回退 collector」—— 那是正确行为
     * （宁可不优化也不能画错），代价是「光影下世界路径没生效」这类问题在现场日志里一个字
     * 都不留。调用方只在门闸<b>已经返回 false</b> 之后才进来，所以逐条重判的成本只发生在
     * 被拒时（每帧最多一次，且与门闸同序）。</p>
     */
    public static String worldSubmitBlocker() {
        if (gpuWorldDisabledThisSession) {
            return "world path disabled by the failure guard (see the earlier ERROR with the stack trace)";
        }
        if (!MeshyConfig.GPU_WORLD.get()) {
            return "MeshGpuWorld=false";
        }
        if (gpuDisabledThisSession) {
            return "GPU path disabled by the master failure guard";
        }
        if (!MeshyConfig.GPU_BAKING.get()) {
            return "MeshGpuBaking=false (master switch)";
        }
        if (!worldFlushAlive()) {
            return "the world flush hook (FeatureRenderDispatcher#renderAllFeatures RETURN) has not run in this or the previous frame";
        }
        if (inHandPass) {
            return "inside the first-person hand pass (its model-view is not the level one)";
        }
        if (ScreenRenderTracker.isRenderingScreen()) {
            return "screen/GUI extraction";
        }
        if (isInsideScopeLevelRender()) {
            return "inside the scope PIP re-render pass";
        }
        if (IrisCompat.isRenderShadow()) {
            return "shadow pass";
        }
        if (IrisCompat.isUsingRenderPack() && !lightmapResolvable()) {
            return "shaders are on but the level lightmap view is unavailable"
                    + " (the only fallback would be the EMISSIVE pipeline, which shader packs"
                    + " light as self-illuminated/unshadowed - see gpuMasterUsable)";
        }
        if (IrisCompat.isUsingRenderPack()) {
            if (!MeshyConfig.GPU_WORLD_UNDER_SHADERS.get()) {
                return "shaders are on and MeshGpuWorldUnderShaders=false (the default)";
            }
            if (!IrisCompat.supportsHandFlushHook()) {
                return "shaders are on but the audited Iris hand-flush hook is unavailable";
            }
        }
        return null;
    }

    /**
     * 总闸：{@code MeshGpuBaking} 打开、本会话没被异常禁用，并且<b>光影下能拿到 lightmap</b>。
     * 两条路（手部/世界）共用，所以这一条同时保护两边。
     *
     * <p>最后那一项是 2026-08-31 加的：拿不到 lightmap 时唯一可用的退化是 EMISSIVE 管线，
     * 而它在光影包里是「自发光、不受阴影」的语义 —— 维护者报的「枪身挡住太阳/月亮那一块反而
     * 继承天体亮度」正是这种照明语义的样子（判别：关掉光影下的 GPU 键现象即消失）。与其用错
     * 语义画，不如这一帧退回 collector（照明由包按正常 entityCutout 路径给）。</p>
     */
    private static boolean gpuMasterUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack() && !lightmapResolvable()) {
            // 这条不打出来的话，第一人称那一半就只能靠「现象没消失」来推断（世界路径另有
            // GPU world submit refused: 那行）。每帧都拒收，所以只报一次，恢复可解析时复位。
            if (!loggedLightmapRefusal) {
                loggedLightmapRefusal = true;
                LOGGER.info("[TacZMeshLoader] GPU path refused while a shader pack is active: the level"
                        + " lightmap view is unavailable, and the only fallback (EMISSIVE) would change"
                        + " the lighting semantics. Staying on the collector route.");
            }
            return false;
        }
        loggedLightmapRefusal = false;
        return true;
    }

    public static boolean isGpuPathUsable() {
        if (!gpuMasterUsable()) {
            return false;
        }
        if (IrisCompat.isUsingRenderPack()) {
            // 第 2 步 v2：光影下的 GPU 路径只在「绘制发生在 Iris 自己那次手部 flush 之内」
            // 时成立（见类注释）。R3 起默认开，但三条仍缺一不可：本开关 + Iris 版本已审计 +
            // 钩子存活证明通过（shouldSubmitGpu 里查）。
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

    /** 世界语境版本：调用方必须已通过 {@link #shouldSubmitGpuWorld} 闸门。 */
    public static void submitBoneWorld(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        WORLD_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /**
     * 把被 LRU 逐出 / 作废的烘焙骨骼交给延迟释放池（下一帧才 close）。
     * 见 {@link #DEFERRED_RELEASE}：本帧可能已有 {@link #WORLD_DRAWS} 条目引用它。
     */
    public static void releaseDeferred(BakedBone bone) {
        if (bone != null) {
            DEFERRED_RELEASE.add(bone);
        }
    }

    /**
     * 申请一次「本帧烘焙额度」。
     *
     * <p>病理场景：同帧出现的世界实体横跨的量化光照档数超过 LRU 容量（比如一排掉落枪
     * 摆在明暗边界上），没有额度闸门就会每帧「逐出-重烘」打摆 —— 烘焙风暴比 collector
     * 还慢。额度用完后本帧余下的枪回退 collector，下一帧额度重置，缓存逐帧收敛到稳态。</p>
     */
    public static boolean tryReserveBake(int cachedLevels) {
        if (bakesThisFrame >= Math.max(4, cachedLevels)) {
            return false;
        }
        bakesThisFrame++;
        return true;
    }

    /**
     * 是否正在执行一次 {@code LevelRenderer#renderLevel(...)}。世界表的消费必须落在它里面 ——
     * {@code renderAllFeatures()} 是公开 API，任何别的语境（GUI/HUD 补 flush、别的 mod 自己调）
     * 命中我们的钩子时，投影与目标都不是世界那套；把消费点圈在「一次世界渲染内部」，
     * 未知调用点最坏也只是「这一帧世界 GPU 没画」而不是「把枪画进 GUI」。
     * 由 {@code GameRendererMixin#tacz$scopeRenderLevel} 用 try/finally 维护（该注入点
     * 本来就存在，为镜内二次渲染而设，同时罩住镜内那遍与主画面那遍）。
     */
    private static boolean levelRenderActive = false;

    public static void setLevelRenderActive(boolean value) {
        levelRenderActive = value;
    }

    /**
     * 「当前是否在开镜镜内那一遍 LevelRenderer 二次渲染里」。
     *
     * <p><b>26.1.2 适配（已随 PIP 深度线移植闭合）</b>：1211 源这里调
     * {@code ScopePipRerender.isInsideScopeLevelRender()}（其 PIP 纪元设施）。此前 26.1.2
     * 无镜内二次渲染，按「纪元差异文档化」裁定为常 false；现在 {@code ScopePipRerender}
     * 已移植（{@code GameRendererMixin} 的 PIP redirect 先跑窄 FOV 那遍），换回真实标志——
     * {@link #shouldSubmitGpuWorld} 的拒收分支与 {@link #renderAtWorldFlush} 的
     * 「画但不清表、不占消费标志」分支自动恢复 1211 语义。标志默认 false
     * （{@code ScopePipRerender} 默认关闭、镜内那遍不跑），主画面路径行为不变。</p>
     */
    private static boolean isInsideScopeLevelRender() {
        return ScopePipRerender.isInsideScopeLevelRender();
    }

    /** 世界钩子的存活证明（帧语义同手部，见 {@link #lastHandFlushFrame}）。 */
    private static boolean worldFlushAlive() {
        return lastWorldFlushFrame == frameId || lastWorldFlushFrame == frameId - 1;
    }

    /**
     * 世界那一次 feature flush 之后的绘制钩子，由 {@code FeatureRenderDispatcherMixin}
     * 在 {@code renderSolidFeatures} 的 RETURN 调用（{@code require=0}）。
     *
     * <p><b>26.1.2 消费点拓扑（CI 探针 round 4 字节码实锤，2026-09-01）</b>：1211 的世界
     * flush 是 frame-graph 主 pass 节点里的 {@code renderAllFeatures()}；26.1.2 拆开了 ——
     * 主 pass lambda（{@code LevelRenderer#renderLevel} 内）依次
     * {@code renderSolidFeatures() → renderTranslucentFeatures() → renderTranslucentParticles()
     * → clearSubmitNodes()}，而 {@code renderAllFeatures()} 只剩手部尾（被 inHandPass 拒）
     * 与 {@code GameRenderer#renderLevel} 尾部（窗口外，被 levelRenderActive 拒）两个站位。
     * 消费点因此必须挂在主 pass 的 {@code renderSolidFeatures} RETURN：同一时刻、同一
     * MV 槽（26.1.2 的 {@code RenderType#draw} 绘制时刻现取）、同一输出目标与深度状态，
     * 与 collector「pose 烘进顶点 + 同一份 MV」逐帧等价。挂 {@code renderAllFeatures}
     * 的旧消费点曾造成两个实机症状：vanilla 世界表永不消费（存活证明永不记录 →
     * 提交侧永久回退 collector = 第三人称/掉落物形态不烘焙）；Iris 26.1 把手部搬进
     * Level 内部后其 flush 落在窗口内但 MV 槽已被手部污染 = 烘焙粘在视界空间。</p>
     *
     * <p><b>与手部钩子的分工</b>：手部表由 {@code ItemInHandRendererMixin} 的钩子在
     * {@code endBatch()} 之后消费（{@code renderAtHandFlush()}）；{@code renderAllFeatures}
     * 的各站位会经由其内部的 {@code renderSolidFeatures} 触发本钩子，但分别被
     * {@code inHandPass} / {@code !levelRenderActive} 拒收且不记存活证明；
     * Iris 的 in-level 手部点由 {@code worldConsumedFrame} 首消费守卫跳过
     * （主 pass 必然先跑、先消费）。</p>
     *
     * <p><b>不清表的情形</b>：镜内那一遍（PIP 二次渲染）与阴影 pass —— 提交每帧只发生一次，
     * 在这里清了主画面就没得画了（表现即「一开镜世界 mesh 枪消失」）。镜内那遍照常画一遍：
     * collector 在镜内那遍是重放的，两遍内容必须一致（本仓 2026-08-30 的既有裁定）。</p>
     */
    public static void renderAtWorldFlush() {
        if (inHandPass) {
            // 手部那一次 renderAllFeatures 不归这里管（表由 ItemInHandRendererMixin 的钩子在
            // endBatch 之后消费），也**不能**在这里记存活证明 —— 否则世界钩子失灵时，手部的
            // 调用点会把「世界钩子活着」这个假象维持下去，世界 submit 就会跳过 collector。
            return;
        }
        if (IrisCompat.isRenderShadow()) {
            // 阴影遍的投影/MV 是太阳视角：既不画也不清（条目属于随后的主画面那一遍）。
            return;
        }
        // 1.21.11 的 always-on-top / gizmo 那一遍会在自己的 frame-graph 节点里把
        // outputColor/DepthTextureOverride 设成离屏 target 再调 renderAllFeatures
        // （CI javap：LevelRenderer.method_75413 里那两处 putstatic）。那一遍既不该画枪
        // （枪不是 always-on-top 内容），也不该在此处开 render pass（可能嵌在别的 pass 里）。
        if (RenderSystem.outputColorTextureOverride != null) {
            return;
        }
        // 只在「正在跑一次 LevelRenderer#renderLevel」时消费：防未知调用点（见
        // {@link #levelRenderActive}）。注意这条检查必须在记存活证明<b>之前</b> ——
        // 若 GameRendererMixin 那个注入点失效，宁可不画也不能把「钩子活着」的假象记进去。
        if (!levelRenderActive) {
            return;
        }
        // 走到这里就是「本帧真正有一个世界 feature flush 的调用点」—— 记存活证明。
        lastWorldFlushFrame = frameId;
        boolean insideScope = isInsideScopeLevelRender();
        if (WORLD_DRAWS.isEmpty() || worldConsumedFrame == frameId) {
            return;
        }
        if (!gpuMasterUsable() || !worldGpuAllowed()) {
            WORLD_DRAWS.clear();
            return;
        }
        try {
            drawList(WORLD_DRAWS, IrisCompat.isUsingRenderPack(), true);
            worldConsecutiveFailures = 0;
            if (!insideScope) {
                worldConsumedFrame = frameId;
            }
        } catch (Exception | LinkageError e) {
            // 次级 pass 嵌在别的 render pass 里时 createRenderPass 会抛；这属于环境不适配，
            // 只关世界路径（手部不受牵连），并清空本帧残留避免反复撞同一处。
            WORLD_DRAWS.clear();
            worldConsecutiveFailures++;
            if (worldConsecutiveFailures >= 30) {
                gpuWorldDisabledThisSession = true;
                LOGGER.error("[TacZMeshLoader] GPU world mesh pass failed {} times in a row; "
                        + "disabling the world GPU path for this session (hand path unaffected).",
                        worldConsecutiveFailures, e);
            } else if (worldConsecutiveFailures == 1) {
                LOGGER.warn("[TacZMeshLoader] GPU world mesh pass failed; skipping world GPU draws "
                        + "until it recovers ({} so far).", worldConsecutiveFailures, e);
            }
        } finally {
            if (!insideScope) {
                WORLD_DRAWS.clear();
            }
        }
    }

    /** 由 GameRendererMixin 在 renderItemInHand HEAD/RETURN 调用。 */
    public static void setInHandPass(boolean value) {
        inHandPass = value;
    }

    public static boolean isInHandPass() {
        return inHandPass;
    }

    /**
     * 26.1.2 的 {@code Lightmap}（前纪元的 {@code LightTexture}）不再提供静态
     * {@code pack(blockLight, skyLight)}；本仓按消费端同一公式内联：
     * {@code (block << 4) | (sky << 20)} —— 与 1.21.11 的
     * {@code LightTexture.pack} 逐位等价（{@code FULL_BRIGHT=0xF000F0} 可复算：
     * {@code (15<<4)|(15<<20)}；{@link #quantizeLight} 的解包方向同源）。
     * 传入值先按 4 位掩码夹紧，行为与上游 pack 一致。
     */
    private static int packLight(int block, int sky) {
        return (block & 0xF) << 4 | (sky & 0xF) << 20;
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
        return packLight(qb, qs);
    }

    /**
     * 本次烘焙<b>必须</b>使用的顶点格式：直接问绘制端（{@code LIT_PIPELINE}）当刻的格式。
     *
     * <p>光影激活时 Iris 的 {@code MixinRenderPipeline#iris$change} 会把 {@code NEW_ENTITY}
     * 替换成它的扩展实体格式（多 at_midBlock / at_tangent / at_midUV，stride 也不同）。
     * pass 消费端读的就是替换后的格式，所以烘焙端必须问同一个 getter，而不能写死
     * {@code DefaultVertexFormat.ENTITY}，否则就是「拉伸的枪模」。无光影时该 getter
     * 原样返回 {@code NEW_ENTITY}。</p>
     */
    public static VertexFormat bakeFormat() {
        VertexFormat format = LIT_PIPELINE.getVertexFormat();
        return format != null ? format : DefaultVertexFormat.ENTITY;
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
        WORLD_DRAWS.clear();
        bakesThisFrame = 0;
        if (!DEFERRED_RELEASE.isEmpty()) {
            // 上一帧被逐出的 VBO：绘制已经结束（上一帧帧末），现在关是安全的。
            for (BakedBone bone : DEFERRED_RELEASE) {
                bone.close();
            }
            DEFERRED_RELEASE.clear();
        }
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
            drawList(HAND_DRAWS, irisFlush, false);
        } catch (Exception | LinkageError e) {
            // LinkageError：光影下这条路径依赖 Iris 的 flush 时机，方法缺失也要能自愈回 collector。
            // 只置内存标志、<b>不回写配置</b>（R3 起；第 1 步沿用了 26.2 的
            // {@code MeshyConfig.GPU_BAKING.set(false)}，那条有两个问题：绘制线程里改配置
            // 可能触发磁盘写；而且用户重启后会看到「GPU 烘焙自己关了」。世界表那边
            // 一开始就是分表 + 阈值语义，见 renderAtWorldFlush。理由详见
            // docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md A2。）
            LOGGER.error("[TacZMeshLoader] GPU mesh hand flush failed (irisFlush={}); "
                    + "falling back to collector path for this session.", irisFlush, e);
            gpuDisabledThisSession = true;
        } finally {
            HAND_DRAWS.clear();
        }
    }

    private static void drawList(List<DrawEntry> draws, boolean irisFlush, boolean worldPass) {
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
            // 登记进对应 program 后 Iris 会按光影包的 gbuffers_hand / gbuffers_entities 为
            // 这条管线编译程序，与 collector 路径下 entityCutout 得到的照明一致。
            // 幂等且失败无害（IrisCompat 内部缓存 + 吞异常）。
            if (worldPass) {
                IrisCompat.assignMeshPipelineToEntity(pipeline);
            } else {
                IrisCompat.assignMeshPipelineToHand(pipeline);
            }
        }

        // 烘焙格式必须与 pass 消费端的格式一致：Iris 激活时 pipeline.getVertexFormat()
        // 会被换成扩展实体格式。不一致就跳过本次绘制并 bump 世代号，让模型在下一次
        // submit 立即重烘（宁少一帧，也不按错 stride 解读 buffer）。
        VertexFormat passFormat = pipeline.getVertexFormat();
        if (passFormat == null) {
            passFormat = DefaultVertexFormat.ENTITY;
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

        // ModelViewMat：直接取 flush 当刻的 getModelViewMatrix()。手部路径的绘制点在
        // 原版/Iris 那次手部 flush 的紧后、世界路径（26.1.2）在主 pass 的
        // renderSolidFeatures 紧后 —— 两者都满足「与 collector 批次同一时刻、同一份槽值」
        // （26.1.2 的 RenderType#draw 在绘制时刻现取该槽），所以「GPU 顶点留骨骼本地 +
        // mv = MV × pose」与 collector「pose 烘进顶点 + MV 原样」逐帧等价。
        // （第 1 步曾在 renderItemInHand RETURN 现取已被还原的栈，才有「相对人物世界位置
        // 恒定」老 bug；1.21.11 侧同样防范了世界路径的这类时刻差 —— 消费点必须在世界
        // 几何自己的 flush 处，26.1.2 拓扑下的落地见 renderAtWorldFlush 的 javadoc。）
        Matrix4f flushMv = new Matrix4f(RenderSystem.getModelViewMatrix());

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
            Matrix4f mv = new Matrix4f(flushMv).mul(entry.model());
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
        // 法线修复用的 MV 栈（见下方 per-draw 的 push/pop 注释；26.2 83daf16 同理移植）。
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

        // 【纹理解析必须在开 pass 之前】与上面「UBO/索引缓冲 pass 前写」同一条不变量：
        // resolveTextureView → TextureManager.getTexture 对未加载的纹理会同步懒加载
        // （registerAndLoad → ReloadableTexture.apply → CommandEncoder.writeToTexture），
        // 而 writeToTexture 在打开的 render pass 里被拒（"Close the existing render pass
        // before performing additional commands"，维护者实机：duyupack kar98un 这类
        // 「全部件都走 GPU、collector 从不请求其 UV 贴图」的高模枪，GPU pass 就是该纹理
        // 的首个请求者 ⇒ 每帧在 pass 内炸 ⇒ 贴图错误）。解析结果（含 missing 回退）
        // 在 pass 外求好，pass 内只做 bindTexture。
        Map<Identifier, GpuTextureView> viewsByTexture = new HashMap<>();
        for (Identifier textureId : byTexture.keySet()) {
            GpuTextureView view = resolveTextureView(textureId);
            if (view == null) {
                view = resolveTextureView(MissingTextureAtlasSprite.getLocation());
            }
            if (view != null) {
                viewsByTexture.put(textureId, view);
            }
        }
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
                GpuTextureView textureView = viewsByTexture.get(group.getKey());
                if (textureView == null) {
                    // pass 外解析失败（连 missing 视图都拿不到）：跳过该组，下一帧重试。
                    continue;
                }
                pass.bindTexture("Sampler0", textureView, linearSampler);

                for (DrawEntry entry : group.getValue()) {
                    // 【法线修复 · 26.2 83daf16 同理移植】绘制执行期间把该骨骼的 pose 压到
                    // RenderSystem MV 栈顶（栈顶 = flushMV × pose_bone），画完立即弹出。
                    // 位置走的是 prepare/writeTransform 的 DynamicTransforms 快照（早已正确），
                    // 但光影包的 gl_NormalMatrix（被 Iris 改名 iris_NormalMat）不来自快照 ——
                    // Iris 26.x ExtendedShader.iris$setupState 在【绘制执行那一刻】读
                    // getModelViewMatrixCopy() 的栈顶做逆转置（iris_ModelViewMatInverse 同源）。
                    // 我们的顶点法线是骨骼本地系裸写（writeRaw），指望这个矩阵补上全部旋转；
                    // 不压栈时 setupState 读到的栈顶只有相机 MV，pose_bone 的旋转层丢失 ⇒
                    // 法线仍朝骨骼本地方向 ⇒ 光影的平行光/反射按错误法线算（「反光的光源
                    // 关系不对」，26.2 用户实测同症状）。vanilla 无光影路径不受此病影响，
                    // 且 pass 内没有别的消费者读这个栈 —— 无条件压/弹保持「栈顶 = 该次
                    // draw 的真实 MV」这一不变量。
                    modelViewStack.pushMatrix();
                    modelViewStack.mul(entry.model());
                    try {
                        pass.setUniform("DynamicTransforms", transformByEntry.get(entry));
                        pass.setVertexBuffer(0, entry.bone().vertexBuffer);
                        pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                        // 1.21.11 drawIndexed(baseVertex, firstIndex, count, instanceCount)：
                        // 顺序索引缓冲 0..count-1，故 baseVertex=0、firstIndex=0、单实例。
                        pass.drawIndexed(0, 0, entry.bone().indexCount, 1);
                    } finally {
                        modelViewStack.popMatrix();
                    }
                }
            }
            boolean already = worldPass ? loggedFirstWorldDraw : loggedFirstDraw;
            if (!already) {
                if (worldPass) {
                    loggedFirstWorldDraw = true;
                } else {
                    loggedFirstDraw = true;
                }
                long indexTotal = 0;
                for (DrawEntry entry : drawable) {
                    indexTotal += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) in {} {} flush:"
                                + " lit={}, colorView={}, depthView={}, vertexFormat={}",
                        drawable.size(), indexTotal, irisFlush ? "Iris" : "vanilla",
                        worldPass ? "world" : "hand", lit,
                        System.identityHashCode(colorView), System.identityHashCode(depthView),
                        passFormat);
            }
        }
    }

    /** 已记录过解析失败的纹理：同一条每帧 ERROR 刷屏没有增量信息（log-once）。 */
    private static final java.util.Set<Identifier> LOGGED_TEXTURE_FAILURES = new java.util.HashSet<>();

    private static GpuTextureView resolveTextureView(Identifier texture) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        } catch (Exception e) {
            // 调用约定：本方法必须在 render pass 之外调用（见 drawList 中段的懒加载说明）。
            if (LOGGED_TEXTURE_FAILURES.add(texture)) {
                LOGGER.error("[TacZMeshLoader] Failed to resolve texture view for {};"
                        + " the group falls back to the missing texture until this succeeds.", texture, e);
            }
            return null;
        }
    }

    /**
     * 当刻能否拿到世界光照贴图的视图。{@code getTextureView()} 是缓存读，可以每帧查。
     *
     * <p>光影下这个值是<b>门闸</b>的一部分（{@link #gpuMasterUsable()}）：拿不到 lightmap 时我们
     * 只会退化到 {@code EMISSIVE_PIPELINE}，而那条管线在光影包眼里是「自发光、不受阴影」——
     * 表现正是几何「继承」天空/天体的亮度（维护者 2026-08-31 实机：把光影下的 GPU 键关掉，现象
     * 立刻消失）。那种退化不是外观降级，是**换了一种照明语义**，所以宁可不进 GPU、留给 collector。</p>
     */
    private static boolean lightmapResolvable() {
        try {
            return Minecraft.getInstance().gameRenderer.lightmap() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static GpuTextureView resolveLightmap(Minecraft mc) {
        try {
            // 26.1.2：Lightmap 类消失，GameRenderer#lightmap() 直接给 GpuTextureView。
            GpuTextureView view = mc.gameRenderer.lightmap();
            if (view == null) {
                if (!loggedLightmapFailure) {
                    loggedLightmapFailure = true;
                    LOGGER.warn("[TacZMeshLoader] Level lightmap view unavailable;"
                            + " drawing this frame with the EMISSIVE pipeline (no lightmap sampling).");
                }
            } else {
                loggedLightmapFailure = false;
            }
            return view;
        } catch (Throwable t) {
            if (!loggedLightmapFailure) {
                loggedLightmapFailure = true;
                LOGGER.warn("[TacZMeshLoader] Failed to read level lightmap;"
                        + " drawing this frame with the EMISSIVE pipeline (no lightmap sampling).", t);
            }
            return null;
        }
    }
}
