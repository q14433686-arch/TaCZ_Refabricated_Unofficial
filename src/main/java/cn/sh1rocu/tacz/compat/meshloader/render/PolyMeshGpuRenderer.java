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
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
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

    /**
     * 【开镜 mesh 枪身裁剪 · 5.2-bis 第 9 项】LIT_PIPELINE 的目镜掩码变体。
     *
     * <p>缺口（26.1.2 分支 ee77059 点名的同款）：collector 提交的枪身经
     * {@code ScopeBodyRenderTypes.clipForViewmodel} 换成 SCOPE_MASK 管线，
     * 开镜时孔径内的枪身像素被 discard；GPU 手部表画的 mesh 枪身走本类自己的
     * 管线，从不经过那次替换 —— mesh 枪管会穿进镜内画面。</p>
     *
     * <p>修法按<b>本仓自己的掩码语义</b>（不是 26.1.2 的深度孔径架构）：
     * {@code core/scope_body} 着色器 = vanilla entity 逐字拷贝 + SCOPE_MASK
     * discard 段，且各 define 分支齐全 —— 给它 NO_OVERLAY + NO_CARDINAL_LIGHTING
     * 就得到与 LIT_PIPELINE 语义一致、只多一步「孔径内 discard」的管线。
     * 掩码纹理在 pass 内直接绑定，与镜身/火光同一张、同一帧语义。</p>
     *
     * <p>启用判据复用 {@code ScopeBodyRenderTypes.maskReadyForViewmodel(true)} ——
     * 与 collector 枪身裁剪<b>同一份</b>前置（掩码开关/光影回退/低倍 sight 的
     * reticle-only 掩码不许裁枪身/掩码 target 就绪），两条路径的裁剪行为
     * 永远同时开关，不会出现「立方体裁了 mesh 没裁」的分叉。</p>
     *
     * <p>光影下不走本管线（useRenderTypeRoute 走 RenderType 路线），那一侧由
     * clipForViewmodel 替换 renderType 覆盖（见 drawViaRenderTypeCore）。</p>
     */
    private static final RenderPipeline LIT_CLIPPED_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity_scope_clipped"))
            .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withShaderDefine("SCOPE_MASK")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withBindGroupLayout(com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.maskSamplerLayout())
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

    /**
     * 世界语境（第三人称/掉落物/展示框/展示台）的登记表。
     *
     * <p>与关 PR 的全局 WORLD_DRAWS 表不同名同义但<b>约束完全不同</b>——泄漏靠
     * 提交侧闸门（{@link #shouldSubmitGpuWorld}）从源头掐死：GUI 语境、GUI 预览
     * 窗口（{@code RenderDistance.isGuiRender()}）、镜内那一遍、阴影 pass 的提交
     * 一概进不来。消费侧只认「主世界那一次 renderAllFeatures」（非手部、非镜内、
     * 非阴影），并且 {@link #beginFrame} 每帧清空 —— 任何一帧内没被消费的残留
     * 都活不过下一帧。</p>
     */
    private static final List<DrawEntry> WORLD_DRAWS = new ArrayList<>();

    /**
     * 延迟释放池：世界烘焙缓存（LRU）被逐出/失效的 VBO 先进这里，
     * 下一帧 {@link #beginFrame} 才真正 close。
     *
     * <p>为什么不能当场 close：同一帧内两个掉落物共享同一个模型实例，
     * 甲的 submit 已把某档光照的 VBO 登记进 {@link #WORLD_DRAWS}，
     * 乙的 submit 触发 LRU 逐出同一档 —— 当场 close 的话，
     * 帧末绘制会引用已销毁的 buffer。绘制永远发生在本帧提交之后、
     * 下一帧 beginFrame 之前，所以「下一帧再关」是最小充分延迟。</p>
     */
    private static final List<BakedBone> DEFERRED_RELEASE = new ArrayList<>();

    private static boolean loggedFirstDraw = false;
    /** 世界表在<b>自定义 pass</b>（无光影/强制自定义）上的首画；与手部的首画各记一次。 */
    private static boolean loggedFirstWorldDrawCustomPass = false;
    private static boolean loggedFirstWorldDraw = false;
    /**
     * 镜内那一遍（PIP 二次渲染）首次画上世界表时记一条 info。
     *
     * <p>用途是把「镜内到底有没有走 GPU 烘焙」从<b>靠帧率反推</b>变成日志里的
     * 一条事实（用户 2026-09-01 的回报只能靠帧率变化倒推）。1.21.11 的
     * {@code 237dc153} 与 26.1.2 的 {@code db360639} 有同名日志，本行是它的
     * 26.2 版：措辞按本线事实写（条目来自本帧 extract 阶段，主画面那遍会再画一遍）。</p>
     */
    private static boolean loggedScopeWorldDraw = false;
    /**
     * 提交侧哨兵：26.2 的世界提交发生在<b>本帧 extract 阶段</b>（镜内那遍开始之前），
     * 所以 {@link #shouldSubmitGpuWorld()} 的镜内分支在正常流程里<b>不可达</b>。
     * 真被走到，说明「提交每帧一次」这条本线前提被打破了（MC 后续版本把提交挪回
     * render 阶段，或某个 mod 在镜内那遍里重新渲染了实体）—— 那时 1.21.11/26.1.2
     * 的那条修复（每遍各自提交 ⇒ 每遍各自消费）才适用于本线。只记一次。
     */
    private static boolean loggedScopeWorldSubmitBlocked = false;
    private static boolean loggedBakeBudget = false;
    private static boolean loggedFirstIrisDraw = false;
    /**
     * 分表禁用（下游审查 A2 采纳）：手部与世界各自独立降级 ——
     * 世界路径出异常不连坐已实机 PASS 的手部路径，反之亦然。
     * <b>只改内存标志，绝不回写配置文件</b>：渲染线程 set() 会把 ForgeConfig
     * spec 弄 dirty 触发磁盘写，且重启后仍是关的（用户得去 TOML 里找回来）。
     */
    private static boolean gpuDisabledThisSession = false;
    private static boolean gpuWorldDisabledThisSession = false;
    private static boolean lightmapUnavailable;
    /**
     * 本帧已画过。Iris 的 HandRenderer 一帧跑两次 renderAllFeatures
     * （solid 与 translucent，两次都会重新 submit），不挡的话同一批骨骼
     * 会被画两遍 —— 不透明几何画两遍视觉无差但白付一倍顶点成本。
     */
    private static boolean drawnThisFrame = false;
    /** 世界表同款「一帧一画」闸门（Iris 一帧两次 renderAllFeatures 的重复消费防线）。 */
    private static boolean worldDrawnThisFrame = false;
    /** 本帧已执行的世界烘焙次数（额度见 {@link #tryReserveBake}）。 */
    private static int bakesThisFrame = 0;
    /**
     * 烘焙世代号：光影包开关每翻转一次 +1（{@link #beginFrame} 逐帧检测）。
     *
     * <p>烘焙产物依赖当时的光影状态——Iris 激活时会扩展实体顶点格式
     * （附加属性、stride 变化），经 {@code BufferBuilder} 写出的常驻 VBO
     * 布局随之不同；切换光影后用新管线按新 stride 解读旧 buffer，
     * 属性错位表现为<b>模型拉伸</b>（用户实测：站着不动开关光影必现，
     * 光照跨档触发重烘后“自愈”）。持有烘焙缓存的模型在 submit 时比对
     * 世代号，不匹配立即重烘——不受光照档节流约束。</p>
     */
    private static int bakeGeneration = 0;
    private static boolean lastShaderPackState = false;
    /** ENTITY 顶点格式的字节 stride 哨兵（A5：光影开关之外的格式变化也要触发整代失效）。 */
    private static int lastEntityStride = -1;

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

    /**
     * 当前这次<b>世界语境</b> submit 是否该走 GPU（登记进 {@link #WORLD_DRAWS}）。
     *
     * <p>提交侧闸门 —— 关 PR 世界表泄漏的每个入口都在这里逐个封死：</p>
     * <ul>
     *   <li>配置 {@code MeshGpuWorld} 打开、GPU 路径本会话可用；</li>
     *   <li><b>不在</b>手部 pass（手部有自己的表；这里进来的只能是世界提取阶段）；</li>
     *   <li><b>不在</b> Screen 提取窗口 —— {@link ScreenRenderTracker} 精确框住
     *       {@code Screen} 的 extract 阶段，背包人偶/枪匠桌预览这类 GUI 内嵌 3D
     *       的 submit 全落在这个窗口里；它们的 pose 是 GUI 投影，落进世界表就是
     *       关 PR 的「枪画进世界」事故。刻意<b>不用</b>
     *       {@code RenderDistance.isGuiRender()}：那是个 100ms 时间戳窗口，
     *       开着菜单时世界提取阶段也会命中，等于「一开背包全场景 mesh 枪
     *       跌回 collector」—— 上游 TML 注释里记载过的同款实机事故；</li>
     *   <li><b>不在</b>镜内那一遍（PIP 二次渲染）—— 防御性闸门：提交都发生在
     *       extract 阶段（镜内那遍开始之前），正常流程根本走不进这个分支；
     *       万一有 mod 在镜内窗口里补提交，pose 语义未知，宁可拒收。
     *       镜内那遍怎么消费世界表见 {@link #renderWorldAfterSolid}
     *       （画但不清表，与 collector 的两遍一致裁定同构）。
     *
     *       <p><b>【与 1.21.11 / 26.1.2 的分歧 —— 本条闸门在本线必须留着】</b>
     *       姊妹线的 {@code 237dc153}(1.21.11) / {@code db360639}(26.1.2)
     *       <b>删掉了</b>同一条闸门，因为它们那两条线的
     *       {@code LevelRenderer#renderLevel} <b>每调用一次就重新提交一遍世界几何</b>
     *       （26.1.2：{@code extractLevel} 每帧一次产状态袋，真正写进
     *       {@code SubmitNodeStorage} 的提交发生在每一遍 render 阶段）——
     *       在那里拒收就等于「镜内那遍只能回 collector + 顶点预算 ⇒ 高模枪被打成
     *       立方体」。<b>26.2 的结构相反</b>：提交（含实体模型渲染）在 extract
     *       阶段一次完成，{@code LevelRenderer#render} 只是绘制阶段；本仓的
     *       {@code SimpleFeatureRenderPhaseMixin} 正是为此存在（镜内那遍把
     *       {@code SubmitNodeStorage} 抽干会让主画面那遍无节点可画，所以取消
     *       phase 的 {@code clear()} 把节点留给主遍）。因此本线上这条闸门<b>不可达</b>，
     *       删它换不到任何东西；而照抄姊妹线「镜内画完清表」的那半会让主画面那遍
     *       拿到空表 ⇒ 开镜 + 二次渲染时<b>镜外的世界 mesh 枪整层消失</b>。
     *       证据链见 {@code docs/MESH_LOADER.md} §5.2-bis 第 13 项。</p></li>
     *   <li><b>不在</b>阴影 pass —— Iris 阴影遍的投影/MV 是太阳视角，
     *       登记进主视角的表必然画错；{@code MeshPolyInShadow=false} 时提交
     *       在 {@code PolyRenderPolicy} 就被拦了，这里是 true 时的第二道保险。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpuWorld() {
        if (!isGpuPathUsable() || gpuWorldDisabledThisSession || !MeshyConfig.GPU_WORLD.get()) {
            return false;
        }
        if (ScopeMaskRenderer.isInHandPass()) {
            return false;
        }
        if (ScreenRenderTracker.isExtractingScreen()) {
            return false;
        }
        if (com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender()) {
            // 本线不可达（世界提交发生在 extract 阶段）—— 真被走到就说明前提变了，
            // 那时才需要照姊妹线那样改成「每遍各自提交、每遍各自消费」。见字段注释。
            if (!loggedScopeWorldSubmitBlocked) {
                loggedScopeWorldSubmitBlocked = true;
                LOGGER.info("[TacZMeshLoader] A world mesh submit was attempted inside the scope PIP re-render "
                        + "pass and was rejected. On 26.2 world submits are produced once per frame during the "
                        + "extract phase, before that pass, so this branch is expected to be unreachable. If this "
                        + "line shows up, the extract-once premise no longer holds on this line and the 1.21.11 "
                        + "(237dc153) / 26.1.2 (db360639) fix -- per-pass submit, per-pass consume -- applies "
                        + "here too. (logged once)");
            }
            return false;
        }
        return !IrisCompat.isRenderShadow();
    }

    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        // 光影下 GPU 路径默认【走 vanilla RenderType 管道】而非自定义 pass：
        // RenderType.prepare() + PreparedRenderType.drawFromBuffer() 用的是
        // entityCutout 的 RenderPipeline —— 该管线已由
        // IrisCompat.assignCommonEntityPipelinesToHandIfNeeded() 归入 Iris HAND
        // program（抛壳/火光同一条兼容链路），Iris 按管线拦截，枪体因此拿到
        // 光影光照。顶点常驻 VBO 不变，每帧仍只写 O(骨骼) 个 DynamicTransforms。
        // MeshGpuUnderShaders=true 时改走自定义 pass（绕开光影管线，无光影光照，
        // 但绘制语义与无光影路径逐位一致 —— 留作排查光影兼容问题的对照组）。
        return true;
    }

    /** 本帧是否该走「vanilla RenderType 管道」变体（光影激活且未强制自定义 pass）。 */
    private static boolean useRenderTypeRoute() {
        return IrisCompat.isUsingRenderPack() && !MeshyConfig.GPU_UNDER_SHADERS.get();
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

    /** 世界语境版本：调用方必须已通过 {@link #shouldSubmitGpuWorld} 闸门。 */
    public static void submitBoneWorld(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        WORLD_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /**
     * 把一个被 LRU 逐出/失效的烘焙骨骼交给延迟释放池（下一帧才 close）。
     * 见 {@link #DEFERRED_RELEASE} 的注释 —— 本帧可能已有 DrawEntry 引用它。
     */
    public static void releaseDeferred(BakedBone bone) {
        if (bone != null) {
            DEFERRED_RELEASE.add(bone);
        }
    }

    /** 挂在 {@code GameRenderer#extract} HEAD（与 ScopeMaskRenderer.beginFrame 同点）。 */
    public static void beginFrame() {
        boolean shaders = IrisCompat.isUsingRenderPack();
        if (shaders != lastShaderPackState) {
            lastShaderPackState = shaders;
            bakeGeneration++;
            LOGGER.info("[TacZMeshLoader] Shader pack state changed (active={}); mesh bake generation -> {}",
                    shaders, bakeGeneration);
        }
        // 【A5 · 第二道格式哨兵】世代号原来只认光影开关翻转；若有别的 mod 也
        // 改写 ENTITY 顶点格式（stride 变化），旧 VBO 会被按新 stride 解读
        // （= 模型拉伸）。逐帧比对格式字节数，变了立刻整代失效。
        int stride = DefaultVertexFormat.ENTITY.getVertexSize();
        if (stride != lastEntityStride) {
            if (lastEntityStride != -1) {
                bakeGeneration++;
                LOGGER.info("[TacZMeshLoader] ENTITY vertex format stride changed ({} -> {}); "
                        + "mesh bake generation -> {}", lastEntityStride, stride, bakeGeneration);
            }
            lastEntityStride = stride;
        }
        HAND_DRAWS.clear();
        WORLD_DRAWS.clear();
        drawnThisFrame = false;
        worldDrawnThisFrame = false;
        bakesThisFrame = 0;
        // LevelRendererWorldPassMixin 的 RETURN 注入在异常路径不触发，
        // 括号标志可能泄漏 —— 每帧兜底归零。
        insideLevelRender = false;
        if (!DEFERRED_RELEASE.isEmpty()) {
            // 上一帧被逐出的 VBO：绘制已经结束（上一帧帧末），现在关是安全的。
            for (BakedBone bone : DEFERRED_RELEASE) {
                bone.close();
            }
            DEFERRED_RELEASE.clear();
        }
    }

    /**
     * 申请一次「本帧烘焙额度」。
     *
     * <p>病理场景：世界里同帧出现的量化光照档数超过 LRU 容量（比如一排掉落枪
     * 横跨明暗边界），没有额度闸门的话，每帧都会「逐出-重烘」打摆 ——
     * 烘焙风暴比 collector 还慢。额度用完后本帧余下的枪回退 collector，
     * 下一帧额度重置，缓存逐帧收敛到稳态。</p>
     */
    public static boolean tryReserveBake() {
        // 额度与 LRU 容量解耦（下游审查 A6 采纳）：容量是显存语义、额度是
        // 每帧 CPU/上传成本语义，一个旋钮当两个用会让「省显存调容量到 1」
        // 的用户额度仍被顶到 4、想调大额度的用户白花显存撑 LRU。
        if (bakesThisFrame >= MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.get()) {
            if (!loggedBakeBudget) {
                loggedBakeBudget = true;
                LOGGER.info("[TacZMeshLoader] World bake budget ({} per frame) exhausted; overflow guns use the "
                        + "collector path this frame and converge over the next frames. Raise "
                        + "MeshGpuBakeBudgetPerFrame if this scene is typical for you. (logged once)",
                        MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.get());
            }
            return false;
        }
        bakesThisFrame++;
        return true;
    }

    /** 当前烘焙世代号。烘焙缓存持有者在 submit 时比对，不匹配须立即重烘。 */
    public static int getBakeGeneration() {
        return bakeGeneration;
    }

    /**
     * 在手部 {@code renderAllFeatures} 的 {@code executeSolid} <b>之后</b>绘制。
     * 世界那次直接清空残留（理论上不应有）。
     */
    public static void renderAfterSolid() {
        // 【PIP 二次渲染 × 光影 —— 必须最先挡】Iris 把手部渲染搬进
        // LevelRenderer.render 内部，于是镜内那一遍也有自己的手部 pass，
        // 本方法会先于主画面那一遍被调到。不挡的话：
        //   1. 镜内那一遍把 HAND_DRAWS 消费掉并置 drawnThisFrame=true；
        //   2. 主画面那一遍重新 submit 的条目被 drawnThisFrame 闸门拦下；
        //   ⇒ 主画面上 mesh 枪件整个消失（只在开镜 + ScopePipRerender + 光影时触发）。
        // 这里清空本遍的提交、不画也不占用 drawnThisFrame —— 主画面那一遍
        // 会重新 submit 一份并正常绘制。镜内内容不受损：合成只取镜片孔径内
        // 的像素，孔径里本来就该是干净的世界画面，不该有枪件。
        //
        if (com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender()) {
            HAND_DRAWS.clear();
            return;
        }
        if (!ScopeMaskRenderer.isInHandPass()) {
            // 非手部的 renderAllFeatures：GUI 图集（GuiItemAtlas /
            // PictureInPictureRenderer）或 renderLevel 偏移 560 的收尾调用。
            // 世界表【不在这里】消费 —— MV-PROBE v2 字节码取证：26.2 的世界
            // 实体 pass 根本不经过 renderAllFeatures（LevelRenderer.render 的
            // 帧图 lambda 直调 PreparedFrame.executeSolid，lambda$addMainPass$0
            // 偏移 177），而 renderLevel 560 处 MV 栈已 popMatrix 回单位阵
            // （LevelRenderer.render 内 30-45 push+mul(viewRotation)、591 pop、
            // 560 在 render 返回之后）—— 首版世界烘焙就是在这里被消费的，
            // 单位阵 MV = 丢相机旋转层 = 「枪固定在视角空间」（实测症状，
            // 与第一人称 0ea0fb6 丢 MV_draw 是同一个病）。
            // 世界的正确消费点在 renderWorldAfterSolid
            // （PreparedFrameSolidMixin，executeSolid RETURN，MV 栈顶=viewRotation）。
            HAND_DRAWS.clear();
            return;
        }
        if (HAND_DRAWS.isEmpty()) {
            return;
        }
        if (RenderSystem.outputColorTextureOverride != null) {
            // 【A1 · 渲染目标覆盖防御】26.2 字节码：override 只在
            // addAlwaysOnTopPass 的 lambda 里设置（世界帧图，且随后复位），
            // vanilla 手部 renderAllFeatures 收尾处不应有 override —— 但别的
            // mod 可以在任何时刻设置它。带着 override 画 = 枪画进未知离屏
            // target。跳过并清表（下一帧手部 pass 会重新 submit）。
            HAND_DRAWS.clear();
            return;
        }
        if (drawnThisFrame) {
            // Iris 第二次手部 pass（renderTranslucent）的重复 submit：跳过。
            HAND_DRAWS.clear();
            return;
        }
        try {
            if (useRenderTypeRoute()) {
                drawListViaRenderType(HAND_DRAWS);
            } else {
                drawList(HAND_DRAWS);
            }
            drawnThisFrame = true;
        } catch (Exception | LinkageError e) {
            // LinkageError 也要接（下游审查 A3 采纳）：渲染路径上最常见的失败
            // 恰是链接类 —— Iris/Sodium 升级后签名变了抛 NoSuchMethodError，
            // 那是 Error 不是 Exception，漏接 = 崩游戏而不是回退 collector。
            // 只置内存标志、不回写配置（A2，理由见字段注释）。
            LOGGER.error("[TacZMeshLoader] GPU hand mesh pass failed; falling back to collector path for this session.", e);
            gpuDisabledThisSession = true;
        } finally {
            HAND_DRAWS.clear();
        }
    }

    /**
     * 「此刻在不在 LevelRenderer.render 里」—— 世界消费点的调用者判据，
     * 由 {@code LevelRendererWorldPassMixin} 在 render 的 HEAD/RETURN 置位。
     * RETURN 在异常路径不触发（镜内那遍的失败被上层捕获），
     * {@link #beginFrame} 每帧兜底归零。
     */
    private static boolean insideLevelRender = false;

    public static void setInsideLevelRender(boolean value) {
        insideLevelRender = value;
    }

    /**
     * 世界 poly_mesh GPU 表的消费点。挂在 {@code PreparedFrame.executeSolid}
     * 的 RETURN（{@code PreparedFrameSolidMixin}）。
     *
     * <h2>为什么是这里（MV-PROBE v2 字节码取证，minecraft-merged-26.2）</h2>
     * <ul>
     *   <li>26.2 世界实体 pass = {@code LevelRenderer.render} 帧图的
     *       {@code lambda$addMainPass$0} <b>直调</b> executeSolid（偏移 177），
     *       不经过 renderAllFeatures —— 首版挂错了地方；</li>
     *   <li>{@code LevelRenderer.render} 开头（30-45）
     *       {@code getModelViewStack().pushMatrix(); mul(viewRotation)}，
     *       帧图执行（572）在 popMatrix（591）之前 ——
     *       <b>本方法执行时 MV 栈顶恰好是 viewRotation</b>，
     *       两个绘制核心（drawList / drawViaRenderTypeCore）从栈顶取 MV_draw
     *       的既有逻辑在这里天然正确，与手部 pass 完全同构。</li>
     * </ul>
     *
     * <h2>executeSolid 的四类调用者怎么分流</h2>
     * <ul>
     *   <li><b>手部 renderAllFeatures</b>（vanilla renderItemInHand 185 /
     *       Iris HandRenderer）：{@code isInHandPass} 拒收 —— 手部有自己的表；</li>
     *   <li><b>GUI renderAllFeatures</b>（GuiItemAtlas /
     *       PictureInPictureRenderer / renderLevel 560 的收尾调用）：
     *       {@code insideLevelRender=false} 拒收；</li>
     *   <li><b>镜内那遍</b>（我们自己驱动的 levelRenderer.render，也在括号内）：
     *       照画但<b>不清表、不占帧标志</b> —— 维护者 08-30 裁定两遍内容必须
     *       一致（collector 也是两遍照画）；WORLD_DRAWS 在 extract 阶段登记、
     *       每帧只一份，镜内清了主画面就没了。无光影时 mainRenderTarget()
     *       已重定向到 pip target、MV 栈顶是镜内那遍自己 push 的 viewRotation
     *       （同一个 render 方法、同一段字节码）—— 语义自动正确。
     *
     *       <p><b>这一条是本线与 1.21.11 / 26.1.2 的实质分歧，不要照抄。</b>
     *       姊妹线的 {@code 237dc153} / {@code db360639} 把这里改成
     *       「镜内那遍画完<b>也清表</b>」，因为在它们那两条线上每一遍
     *       {@code renderLevel} 都会重新提交一份世界几何（镜内那遍有自己的表，
     *       不清就会被主遍再叠画一遍）。本线的提交只发生在 extract 阶段
     *       （{@code LevelRenderer#render} 是纯绘制阶段，本仓
     *       {@code SimpleFeatureRenderPhaseMixin} 就是为了让主遍能再取一次同一批
     *       节点而存在的）—— 在这里清表 = 主画面那遍拿到空表 = 开着二次渲染开镜时
     *       <b>镜外的世界 mesh 枪整层消失</b>。所以本线保持「画而不清」，
     *       并在首次发生时打一条 log-once（{@link #loggedScopeWorldDraw}）
     *       把「镜内确实在走 GPU 烘焙」变成日志事实。详见
     *       {@code docs/MESH_LOADER.md} §5.2-bis 第 13 项。</p></li>
     *   <li><b>主世界帧图</b>：消费 + 置帧标志 + 清表。</li>
     * </ul>
     *
     * <p>阴影 pass（Iris 有自己的渲染循环，理论到不了这里）：只跳过、
     * <b>不清表</b> —— 它若真跑到，也发生在主 gbuffers 遍之前，
     * 清表等于把主画面的条目扔了。</p>
     *
     * <p>时机安全性：executeSolid 内部逐 phase 开/关各自的 render pass，
     * RETURN 处不在任何 pass 内，createRenderPass 断言安全；
     * 立方体/地形深度已就绪，GPU poly 同一张 depth view 深度测试即正确遮挡。</p>
     */
    public static void renderWorldAfterSolid() {
        if (!insideLevelRender) {
            return;
        }
        if (ScopeMaskRenderer.isInHandPass()) {
            // Iris 把手部 renderAllFeatures 搬进 LevelRenderer.render 内部，
            // 括号内也可能出现手部的 executeSolid —— 那是手部表的事，这里不碰。
            return;
        }
        if (IrisCompat.isRenderShadow()) {
            return;
        }
        if (WORLD_DRAWS.isEmpty()) {
            return;
        }
        if (RenderSystem.outputColorTextureOverride != null) {
            // 【A1】带 override 的 executeSolid（26.2 里 vanilla 不存在这种组合，
            // 防的是 mod 注入的离屏遍）：跳过且【不清表】—— 条目属于其后
            // 真正的主世界遍。
            return;
        }
        boolean inScopePass = com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender();
        if (!inScopePass && worldDrawnThisFrame) {
            // 主世界重复消费（防御性；正常一帧只有一次主世界帧图）。
            WORLD_DRAWS.clear();
            return;
        }
        try {
            if (useRenderTypeRoute()) {
                drawWorldListViaRenderType(WORLD_DRAWS);
            } else {
                drawList(WORLD_DRAWS);
            }
            if (!inScopePass) {
                worldDrawnThisFrame = true;
                WORLD_DRAWS.clear();
            } else if (!loggedScopeWorldDraw) {
                // 镜内那遍：表保留原样，主画面那遍再消费。
                // 这条 log-once 是本线「镜内是否走 GPU 烘焙」的唯一硬证据 ——
                // 用户此前只能靠帧率变化反推（2026-09-01 回报）。
                loggedScopeWorldDraw = true;
                LOGGER.info("[TacZMeshLoader] GPU world mesh pass active inside the scope PIP re-render pass: "
                                + "drew {} world entries that were submitted during this frame's extract phase. "
                                + "26.2 submits world geometry once per frame (in extract), so the very same "
                                + "entries are drawn again by the main pass -- the table is deliberately NOT "
                                + "cleared here. (logged once)",
                        WORLD_DRAWS.size());
            }
        } catch (Exception | LinkageError e) {
            // 分表禁用（A2）：世界路径失败只关世界闸，不连坐手部；
            // 不回写配置；LinkageError 一并接住（A3）。
            LOGGER.error("[TacZMeshLoader] GPU world mesh pass failed; disabling the world GPU path for this session "
                    + "(first-person GPU baking unaffected).", e);
            gpuWorldDisabledThisSession = true;
            WORLD_DRAWS.clear();
        }
    }

    /**
     * 【光影路径】vanilla RenderType 管道变体：常驻 VBO + 官方 prepare()/drawFromBuffer。
     *
     * <h2>为什么这条路在 Iris 下能拿到光影光照</h2>
     * Iris 按 {@code RenderPipeline} 对象拦截绘制（本仓证据链：抛壳/火光用 vanilla
     * ENTITY_CUTOUT 提交，经 {@code assignCommonEntityPipelinesToHandIfNeeded()}
     * 归入 HAND program 后光影下渲染正确）。这里用 {@code RenderTypes.entityCutout}
     * —— 管线正是那条链路已注册的 ENTITY_CUTOUT，Iris 对它的接管方式与 vanilla
     * 立方体/collector poly 完全一致。
     *
     * <h2>每骨骼矩阵怎么进去（字节码依据：RenderType.prepare() 偏移 55-58）</h2>
     * prepare() 内部 {@code getModelViewMatrixCopy() -> writeDynamicTransforms(mv)}
     * —— 从 ModelView 栈顶取矩阵。所以把 {@code MV_hand × pose_bone} 压栈 →
     * prepare() → 弹栈，每骨骼得到一份独立的 DynamicTransforms 切片，
     * 顶点仍留在骨骼本地系的常驻 VBO 里，零 CPU 变换。
     *
     * <p>{@code drawFromBuffer(vb, ib, type, 0, 0, indexCount)} 的参数序按其字节码
     * {@code drawIndexed(arg6, 1, arg5, arg4, 0)} 与已实测正确的
     * {@code drawIndexed(indexCount, 1, 0, 0, 0)} 对齐：arg6=indexCount，其余置 0。</p>
     *
     * <h2>视觉对齐</h2>
     * entityCutout = PER_FACE_LIGHTING + overlay + lightmap，与 collector 路径
     * 同一 RenderType —— 无光影时两条路视觉逐位一致；顶点里 UV1=NO_OVERLAY、
     * UV2=量化光照，语义同 collector 写入。
     */
    private static void drawListViaRenderType(List<DrawEntry> draws) {
        IrisCompat.assignCommonEntityPipelinesToHandIfNeeded();
        long totalIndices = drawViaRenderTypeCore(draws, true);
        if (!loggedFirstIrisDraw) {
            loggedFirstIrisDraw = true;
            LOGGER.info("[TacZMeshLoader] GPU mesh pass (RenderType route, shader-pack compatible) drew {} bones "
                    + "({} indices) on hand pass.", draws.size(), totalIndices);
        }
    }

    /**
     * 【光影 · 世界 pass】RenderType 管道变体。
     *
     * <h2>「管线被归入 HAND 后世界枪也走 HAND」是误读（下游审查 A4 · 已核实驳回）</h2>
     * Iris 26.2 的 {@code IrisPipelines} 静态表把 vanilla ENTITY_CUTOUT 映射到
     * {@code getCutout(p)} —— 一个<b>逐 draw 求值</b>的函数：
     * {@code HandRenderer.INSTANCE.isActive()} 时给 HAND_CUTOUT_DIFFUSE，
     * 否则给 ENTITIES_CUTOUT_DIFFUSE（gbuffers_entities）。分派是按「绘制那一刻
     * 在不在手部」动态判的，不是按管线对象一锤定音。而且
     * {@code IrisPipelines.assignPipeline} 对已在静态表里的管线直接抛
     * "Shader already assigned" —— 我们 IrisCompat 把该异常当成功吞掉，
     * 即那次 assign 对 vanilla 管线本来就是 no-op。世界枪在世界 pass 里
     * 消费（HandRenderer 非活跃）⇒ 必然落 entities 程序。（源码依据：
     * IrisPipelines.java 26.2 分支 assignToMain/getCutout/assignPipeline 三段。）
     *
     * <p>与手部变体唯一的语义差异：<b>不调</b>
     * {@code assignCommonEntityPipelinesToHandIfNeeded()} —— 那是手部 pass 的
     * 专项修复（把抛壳用的 vanilla 管线归入 Iris HAND program）。世界 pass 里
     * ENTITY_CUTOUT 就是 vanilla 世界实体在用的管线，Iris 对它的默认接管
     * （gbuffers_entities 链路）正是我们想要的；这里主动去动管线归属反而可能
     * 干扰别的实体。绘制机制（prepare() 压栈取 MV × drawFromBuffer）与手部
     * 完全同构，两层变换定理不区分 pass。</p>
     */
    private static void drawWorldListViaRenderType(List<DrawEntry> draws) {
        long totalIndices = drawViaRenderTypeCore(draws, false);
        if (!loggedFirstWorldDraw) {
            loggedFirstWorldDraw = true;
            LOGGER.info("[TacZMeshLoader] GPU world mesh pass (RenderType route) drew {} bones "
                    + "({} indices) on world pass.", draws.size(), totalIndices);
        }
    }

    /**
     * @param handPass 手部表才裁目镜（{@code clipForViewmodel}）；世界表不裁 ——
     *                 世界枪本就该出现在镜内画面里，与 collector 的世界枪一致。
     */
    private static long drawViaRenderTypeCore(List<DrawEntry> draws, boolean handPass) {
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        long totalIndices = 0;
        for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
            // 【开镜 mesh 枪身裁剪 · 光影侧】与 collector 枪身完全同一份机制：
            // clipForViewmodel 在掩码就绪时把 entityCutout 换成 scope_body_clipped
            // （= entityCutout 配方 + SCOPE_MASK discard，视觉逐状态一致）。
            // 该管线已由 assignScopePipelineToHand 归入 Iris HAND program，
            // IrisScopeMaskState 对它写 tacz_ScopeMaskMode=1 —— 立方体枪身在
            // 光影下裁剪正确走的就是这条已实证链路，mesh 只是同车。
            // 掩码不就绪/未开镜时原样返回 entityCutout，行为与改动前逐位相同。
            RenderType renderType = handPass
                    ? com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.clipForViewmodel(
                            RenderTypes.entityCutout(group.getKey()), group.getKey(), true)
                    : RenderTypes.entityCutout(group.getKey());
            for (DrawEntry entry : group.getValue()) {
                // MV = MV_draw(栈顶) × pose_bone。压栈让 prepare() 自己取。
                //
                // 【弹栈必须在 drawFromBuffer 之后 —— 法线病灶】首版在 prepare()
                // 后立即弹栈，位置对但光照/反光全错。根因（Iris 26.2
                // ExtendedShader.iris$setupState 源码实读）：
                //
                //   if (normalMat > -1) {
                //       tempF = RenderSystem.getModelViewMatrixCopy()
                //           .invert(tempMatrix4f).transpose3x3(normalMatrix).get(tempF);
                //       IrisRenderSystem.uniformMatrix3fv(normalMat, false, tempF);
                //   }
                //
                // 光影包的 gl_NormalMatrix（被 Iris 改名 iris_NormalMat）不来自
                // prepare() 快照的 DynamicTransforms，而是【绘制执行那一刻】的
                // RenderSystem MV 栈顶的逆转置（iris_ModelViewMatInverse 同源）。
                // 我们的顶点法线是骨骼本地系（writeRaw 裸写），指望这个矩阵补上
                // 全部旋转 —— 弹早了，setupState 读到的栈顶只剩 MV_draw，
                // pose_bone 的旋转层丢失 ⇒ 法线仍朝骨骼本地方向 ⇒ 光影的
                // 平行光/反射按错误法线算 ⇒「反光的光源关系不对」（实测症状）。
                // 位置不受影响：ModelViewMat 走的是 prepare() 快照，早已正确。
                //
                // vanilla 无光影路径不受此病影响：核心 entity shader 的
                // NO_CARDINAL_LIGHTING 分支根本不用法线。
                mvStack.pushMatrix();
                mvStack.mul(entry.model());
                try {
                    PreparedRenderType prepared = renderType.prepare();
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    GpuBuffer indexBuffer = indices.getBuffer(entry.bone().indexCount);
                    prepared.drawFromBuffer(entry.bone().vertexBuffer, indexBuffer, indices.type(),
                            0, 0, entry.bone().indexCount);
                } finally {
                    mvStack.popMatrix();
                }
                totalIndices += entry.bone().indexCount;
            }
        }
        return totalIndices;
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
        // 绘制时 ModelViewMat = RenderSystem 当刻的 MV。GPU 路径顶点在骨骼本地系、
        // pose 写进 DynamicTransforms，若只写 entry.model() 就丢了 MV_draw 这一层
        // —— 枪位置对（pose 带平移）但朝向恒北、俯仰为平，实测症状完全吻合。
        //
        // 【前置条件 · A7】本方法被手部/世界两张表共用，两层变换定理在两个 pass
        // 都成立的前提是：调用时刻的 MV 栈顶必须正是这批 pose 所属 pass 的 MV_draw。
        //   手部：renderItemInHand 在 renderAllFeatures 前后 push/pop arg3
        //        （字节码 96-110/190），executeSolid 后消费 ⇒ 栈顶 = 手部 MV ✓
        //   世界：LevelRenderer.render 在帧图前后 push/pop viewRotation
        //        （30-45/591），executeSolid RETURN 消费 ⇒ 栈顶 = viewRotation ✓
        // 挂错消费点（如 renderLevel 偏移 560，栈已 pop）= 首版「枪固定在
        // 视角空间」事故。改消费点必须重新验证这条前提。
        Matrix4f drawModelView = RenderSystem.getModelViewMatrixCopy();

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        // 【纹理必须在 render pass 之外解析 —— 05170 实机踩坑 2ae4c29 移植】
        // TextureManager.getTexture 对未加载纹理是懒加载：registerAndLoad ->
        // ReloadableTexture.apply -> CommandEncoder.writeToTexture，而«pass 开着
        // 不许发其他命令»的约束会让这次上传直接抛异常。全 GPU 提交的枪
        // （每个可见部件都走 GPU 表）没有 collector 兄弟先去请求贴图，
        // 我们就是第一个请求者 —— 在 pass 里解析 = 贴图永远加载不上、
        // 每帧报错 + 枪面全紫黑（26.1.2 duyupack kar98un 实机复现；本仓
        // resolveTextureView 与其逐字相同，世界 GPU 路径上线后同样暴露）。
        Map<Identifier, GpuTextureView> viewsByTexture = new HashMap<>();
        for (Identifier texture : byTexture.keySet()) {
            GpuTextureView view = resolveTextureView(texture);
            if (view == null) {
                view = resolveTextureView(MissingTextureAtlasSprite.getLocation());
            }
            if (view != null) {
                viewsByTexture.put(texture, view);
            }
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // 【开镜 mesh 枪身裁剪】与 collector 枪身的 clipForViewmodel 同一份判据
        // （maskReadyForViewmodel）：掩码可用 + 允许裁视模时，手部表换用
        // SCOPE_MASK 变体管线，孔径内的 mesh 枪身像素 discard —— 修掉
        // 「mesh 枪管穿进镜内画面」（5.2-bis 第 9 项）。仅手部表：世界枪不裁
        // （它们本来就该出现在镜内画面里，与 collector 的世界枪一致）。
        // EMISSIVE 回退档不裁：lightmap 都拿不到的会话已在降级态，宁简勿繁。
        boolean clipAgainstOcular = draws == HAND_DRAWS
                && lightmapView != null
                && com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.maskReadyForViewmodel(true);
        GpuTextureView maskView = null;
        if (clipAgainstOcular) {
            RenderTarget maskTarget = com.tacz.guns.client.render.scope.ScopeMaskTarget.current();
            maskView = maskTarget != null ? maskTarget.getColorTextureView() : null;
            clipAgainstOcular = maskView != null;
        }

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
            pass.setPipeline(clipAgainstOcular ? LIT_CLIPPED_PIPELINE : (lit ? LIT_PIPELINE : EMISSIVE_PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            if (lit) {
                pass.bindTexture("Sampler2", lightmapView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }
            if (clipAgainstOcular) {
                // NEAREST 与 ScopeMaskTextureHandle 的理由相同：掩码是二值数据，
                // 线性过滤会让 > 0.5 判定在边界抖动出毛边。
                pass.bindTexture(com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.maskSamplerName(),
                        maskView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }

            for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
                GpuTextureView textureView = viewsByTexture.get(group.getKey());
                if (textureView == null) {
                    continue;
                }
                pass.bindTexture("Sampler0", textureView, linearSampler);

                for (DrawEntry entry : group.getValue()) {
                    // ModelViewMat = MV_draw * pose_submit（乘序同 vanilla：顶点先套
                    // pose 再进相机系）。scratch 每骨骼重算，不污染 entry.model()。
                    Matrix4f mv = new Matrix4f(drawModelView).mul(entry.model());
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(mv, WHITE));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer.slice());
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                    pass.drawIndexed(entry.bone().indexCount, 1, 0, 0, 0);
                }
            }
            // 两张表各记一次首画：世界表在自定义 pass 上的首画是「世界 mesh 枪
            // 确实走了 GPU 烘焙」的证据之一，与手部共用一个标志会被手部抢先吃掉
            // （手部 pass 一帧内更早）。光影 RenderType 路线另有 loggedFirstWorldDraw。
            boolean handTable = draws == HAND_DRAWS;
            if (handTable ? !loggedFirstDraw : !loggedFirstWorldDrawCustomPass) {
                if (handTable) {
                    loggedFirstDraw = true;
                } else {
                    loggedFirstWorldDrawCustomPass = true;
                }
                long indices = 0;
                for (DrawEntry entry : draws) {
                    indices += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) on the {} pass, lit={}",
                        draws.size(), indices, handTable ? "hand" : "world", lit);
            }
        }
    }

    /** 解析失败按纹理去重打日志（全 GPU 枪失败时逐帧重试，不去重会刷屏）。 */
    private static final java.util.Set<Identifier> LOGGED_TEXTURE_FAILURES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static GpuTextureView resolveTextureView(Identifier texture) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        } catch (Exception e) {
            if (LOGGED_TEXTURE_FAILURES.add(texture)) {
                LOGGER.error("[TacZMeshLoader] Failed to resolve texture view for {} (logged once)", texture, e);
            }
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
